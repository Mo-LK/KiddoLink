package com.childapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.childapp.ble.BleAdvertiser
import com.childapp.ble.BleScanner
import com.childapp.storage.PreferencesManager
import com.childapp.ui.theme.KiddoLinkTheme
import com.childapp.ui.IdSetupScreen
import com.childapp.ui.MainScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.childapp.service.BleForegroundService


class ChildMainActivity : ComponentActivity() {

    private lateinit var bleScanner: BleScanner
    private lateinit var bleAdvertiser: BleAdvertiser
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = PreferencesManager(this)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted && prefs.hasId()) {
                val intent = Intent(this, BleForegroundService::class.java)
                startForegroundService(intent)
                startBleOperations(prefs.getId()!!)
            } else {
                Log.e("PERMISSIONS", "Permissions or ID missing")
            }
        }

        setContent {
            KiddoLinkTheme {
                val deviceIdState = remember { mutableStateOf(prefs.getId()) }

                if (deviceIdState.value.isNullOrBlank()) {
                    IdSetupScreen(onIdSet = { id ->
                        prefs.saveId(id)
                        deviceIdState.value = id
                        requestPermissions()
                    })
                } else {
                    MainScreen(deviceIdState.value!!)
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startBleOperations(myId: String) {
        bleScanner = BleScanner(this)
        bleScanner.startScanning()

        bleAdvertiser = BleAdvertiser(this, myId)
        bleAdvertiser.startAdvertising()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bleScanner.isInitialized) bleScanner.stopScanning()
        if (::bleAdvertiser.isInitialized) bleAdvertiser.stopAdvertising()
    }
}
