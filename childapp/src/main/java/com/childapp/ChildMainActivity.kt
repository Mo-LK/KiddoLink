package com.childapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.childapp.ble.BleScanner
import com.childapp.ui.theme.KiddoLinkTheme

class ChildMainActivity : ComponentActivity() {

    private lateinit var bleScanner: BleScanner
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleScanner = BleScanner(this)

        Log.d("BLE_PERMISSION", "Launching permission request…")
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }

            if (allGranted) {
                startBleOperations()
            } else {
                Log.e("PERMISSIONS", "One or more permissions were denied")
            }
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            KiddoLinkTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("ChildApp - BLE Mesh") }
                        )
                    }
                ) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun startBleOperations() {
        bleScanner.startScanning()
        // TODO: ajouter bleAdvertiser.startAdvertising() quand sera codé
    }

    override fun onDestroy() {
        super.onDestroy()
        bleScanner.stopScanning()
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "BLE Scanning Active...", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Waiting for nearby devices...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    KiddoLinkTheme {
        MainScreen()
    }
}