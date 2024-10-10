package com.example.wificonnector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.wificonnector.ui.theme.WifiConnectorTheme

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiManager
    private var scanResults = mutableStateListOf<ScanResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Request location permissions
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineLocationGranted || coarseLocationGranted) {
                if (checkPermissions()) {
                    scanForWifiNetworks() // Start scanning after permissions are granted
                } else {
                    Log.d("WiFi", "Location permissions not granted.")
                }
            } else {
                Log.d("Permissions", "Location permissions denied")
            }
        }

        requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )

        // Register receiver for Wi-Fi scan results
        registerWifiScanReceiver()

        // Set up the UI using Jetpack Compose
        setContent {
            WifiConnectorTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        WifiScreen(
                            modifier = Modifier.padding(innerPadding),
                            scanResults = scanResults,
                            onConnect = { scanResult -> connectToWifi(scanResult.SSID ?: "Unknown SSID", scanResult.BSSID) }
                        )
                    }
                )
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted || coarseLocationGranted
    }


    private fun scanForWifiNetworks() {
        // Explicitly check for the permission before calling wifiManager.startScan()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For API 29+ (Android 10 and above), handle using WifiNetworkSpecifier
                } else {
                    wifiManager.startScan() // This is deprecated but needed for older devices
                }
            } catch (e: SecurityException) {
                Log.d("WiFi", "Permission not granted: ${e.message}")
            }
        } else {
            Log.d("WiFi", "Permission not granted for scanning Wi-Fi networks.")
        }
    }

    private fun updateScanResults() {
        // Check for location permission before accessing the scan results
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                scanResults.clear()
                scanResults.addAll(wifiManager.scanResults) // Access scan results safely
            } catch (e: SecurityException) {
                Log.d("WiFi", "Permission not granted: ${e.message}")
            }
        } else {
            Log.d("WiFi", "Location permission not granted. Cannot access Wi-Fi scan results.")
        }
    }

    // Register the Wi-Fi scan receiver to listen for scan results
    private fun registerWifiScanReceiver() {
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    updateScanResults() // Update scan results when available
                } else {
                    Log.d("WiFi", "Wi-Fi scan failed.")
                }
            }
        }
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    // Connect to Wi-Fi using WifiNetworkSpecifier (API 29+)
    private fun connectToWifi(ssid: String, bssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setBssid(android.net.MacAddress.fromString(bssid))
                .build()

            val wifiNetworkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            connectivityManager.requestNetwork(wifiNetworkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d("WiFi", "Connected to Wi-Fi")
                }
            })
        } else {
            Log.d("WiFi", "WifiNetworkSpecifier only available on API 29+")
        }
    }
}

@Composable
fun WifiScreen(modifier: Modifier = Modifier, scanResults: List<ScanResult>, onConnect: (ScanResult) -> Unit) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Available Wi-Fi Networks", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        // List available Wi-Fi networks
        if (scanResults.isNotEmpty()) {
            for (result in scanResults) {
                WifiNetworkItem(scanResult = result, onConnect = onConnect)
            }
        } else {
            Text(text = "No Wi-Fi networks found")
        }
    }
}

@Composable
fun WifiNetworkItem(scanResult: ScanResult, onConnect: (ScanResult) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = scanResult.SSID ?: "Unknown SSID")
        Button(onClick = { onConnect(scanResult) }) {
            Text(text = "Connect")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WifiScreenPreview() {
    WifiConnectorTheme {
        WifiScreen(scanResults = listOf(), onConnect = {})
    }
}
