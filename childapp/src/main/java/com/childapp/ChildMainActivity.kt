package com.childapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.childapp.ble.BleAdvertiser
import com.childapp.ble.BleScanner
import com.childapp.storage.PreferencesManager
import com.childapp.ui.theme.KiddoLinkTheme
import com.childapp.ui.IdSetupScreen
import com.childapp.ui.MainScreen
import com.childapp.service.BleForegroundService
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

class ChildMainActivity : ComponentActivity() {

    private lateinit var bleScanner: BleScanner
    private lateinit var bleAdvertiser: BleAdvertiser
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var prefs: PreferencesManager
    private var bleJob: Job? = null
    private var scanJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = PreferencesManager(this)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted && prefs.hasId()) {
                startBleServiceAndOperations(prefs.getId()!!)
            }
        }

        setContent {
            KiddoLinkTheme {
                val deviceIdState = remember { mutableStateOf(prefs.getId()) }
                val nearbyDevicesState = remember { mutableStateOf(mapOf<String, String>()) }

                fun startRealtimeScan() {
                    scanJob?.cancel()
                    scanJob = lifecycleScope.launch {
                        while (isActive) {
                            val devices = bleScanner.lastDetectedDevices
                                .filter { it.estimatedDistance <= 2.0 }
                                .associate { it.deviceId to "${"%.2f".format(it.estimatedDistance)}m" }
                            nearbyDevicesState.value = devices
                            delay(1000)
                        }
                    }
                }

                if (deviceIdState.value.isNullOrBlank()) {
                    IdSetupScreen(onIdSet = { id ->
                        prefs.saveId(id)
                        deviceIdState.value = id
                        if (hasAllPermissions()) {
                            startBleServiceAndOperations(id)
                        } else {
                            requestPermissions()
                        }
                    })
                } else {
                    MainScreen(
                        deviceId = deviceIdState.value!!,
                        onScan = { startRealtimeScan() },
                        nearbyDevices = nearbyDevicesState.value.map { "${it.key} @ ${it.value}" }
                    )
                }
            }
        }

        if (prefs.hasId() && hasAllPermissions()) {
            startBleServiceAndOperations(prefs.getId()!!)
        } else if (prefs.hasId()) {
            requestPermissions()
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

    private fun hasAllPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startBleServiceAndOperations(myId: String) {
        val intent = Intent(this, BleForegroundService::class.java)
        startForegroundService(intent)
        startBleOperations(myId)
        startPeriodicProximityReport(myId)
    }

    private fun startBleOperations(myId: String) {
        bleScanner = BleScanner(this)
        bleScanner.startScanning()
        bleAdvertiser = BleAdvertiser(this, myId)
        bleAdvertiser.startAdvertising()
    }

    private fun startPeriodicProximityReport(myId: String) {
        bleJob?.cancel()
        val db = FirebaseFirestore.getInstance()
        bleJob = lifecycleScope.launch(Dispatchers.IO) {
            val now = Calendar.getInstance()
            val minutes = now.get(Calendar.MINUTE)
            val delayMillis = ((ceil(minutes / 5.0) * 5 - minutes) * 60 * 1000).toLong()
            delay(delayMillis)
            while (isActive) {
                val nearbyIds = bleScanner.getNearbyDeviceIds(2.0)
                val isAlone = nearbyIds.isEmpty()
                val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val report = hashMapOf(
                    "deviceId" to myId,
                    "time" to currentTime,
                    "isAlone" to isAlone,
                    "nearbyIds" to nearbyIds
                )
                db.collection("presence_reports")
                    .document("${myId}_${currentTime}")
                    .set(report)
                delay(5 * 60 * 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleJob?.cancel()
        scanJob?.cancel()
        if (::bleScanner.isInitialized) bleScanner.stopScanning()
        if (::bleAdvertiser.isInitialized) bleAdvertiser.stopAdvertising()
    }
}
