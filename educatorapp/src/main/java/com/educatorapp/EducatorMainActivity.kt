package com.educatorapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.educatorapp.ble.BleScanner
import com.educatorapp.service.BleForegroundService
import com.educatorapp.ui.NearbyChildrenScreen
import com.educatorapp.ui.theme.KiddoLinkTheme

class EducatorMainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            permissionsGranted = allGranted
        }

        if (hasAllPermissions(requiredPermissions)) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            KiddoLinkTheme {
                val scannerState = remember { mutableStateOf<BleScanner?>(null) }

                LaunchedEffect(permissionsGranted) {
                    if (permissionsGranted && scannerState.value == null) {
                        startForegroundBleService()
                        scannerState.value = BleScanner(this@EducatorMainActivity).also {
                            it.startScanning()
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val scanner = scannerState.value
                    if (permissionsGranted && scanner != null) {
                        NearbyChildrenScreen(
                            scanner = scanner,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        Text(
                            text = "Waiting for permissions...",
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun startForegroundBleService() {
        val intent = Intent(this, BleForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun hasAllPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
