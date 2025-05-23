package com.childapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.childapp.ble.BleAdvertiser
import com.childapp.ble.BleScanner
import com.childapp.service.BleForegroundService
import com.childapp.storage.PreferencesManager
import com.childapp.ui.IdSetupScreen
import com.childapp.ui.MainScreen
import com.childapp.ui.theme.KiddoLinkTheme
import com.childapp.sensors.MotionSensorManager
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
    private var motionSensorManager: MotionSensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PreferencesManager(this)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            Log.d("PERMISSION", "All permissions granted: $allGranted")
            if (allGranted && prefs.hasId()) {
                startBleServiceAndOperations(prefs.getId()!!)
            }
        }

        setContent {
            KiddoLinkTheme {
                val deviceIdState = remember { mutableStateOf(prefs.getId()) }
                val nearbyDevicesState = remember { mutableStateOf(mapOf<String, String>()) }
                val motionTriggerState = remember { mutableStateOf(false) }
                val savedFilePath = remember { mutableStateOf<String?>(null) }

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
                        nearbyDevices = nearbyDevicesState.value.map { "${it.key} @ ${it.value}" },
                        motionTriggered = motionTriggerState.value,
                        lastSavedFilePath = savedFilePath.value
                    )
                }

                if (prefs.hasId()) {
                    startMotionTriggerListener(prefs.getId()!!, motionTriggerState, savedFilePath)
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
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BODY_SENSORS
            )
        )
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS
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
                    .document("${myId}_$currentTime")
                    .set(report)
                delay(5 * 60 * 1000)
            }
        }
    }

    private fun startMotionTriggerListener(
        deviceId: String,
        motionTriggerState: androidx.compose.runtime.MutableState<Boolean>,
        savedFilePath: androidx.compose.runtime.MutableState<String?>
    ) {
        val db = FirebaseFirestore.getInstance()
        motionSensorManager = MotionSensorManager(this, deviceId)

        db.collection("triggers")
            .document(deviceId)
            .addSnapshotListener { snapshot, _ ->
                val shouldStart = snapshot?.getBoolean("motion_triggered") ?: false
                val shouldStop = snapshot?.getBoolean("motion_stop") ?: false

                if (shouldStart) {
                    Log.d("TRIGGER", "Starting motion recording for $deviceId")
                    motionSensorManager?.start {
                        Log.d("TRIGGER", "Motion data saving initiated for $deviceId")
                        db.collection("triggers")
                            .document(deviceId)
                            .update("upload_complete", true)
                    }
                    motionTriggerState.value = true

                    db.collection("triggers").document(deviceId)
                        .update(mapOf("motion_triggered" to false, "recording" to true))
                }

                if (shouldStop) {
                    Log.d("TRIGGER", "Stopping motion recording for $deviceId")
                    motionSensorManager?.stopAndGetPath { path ->
                        motionTriggerState.value = false

                        if (path != null) {
                            Log.d("TRIGGER", "Motion data file saved at: $path")
                            savedFilePath.value = path
                            Toast.makeText(this, "Data saved to: $path", Toast.LENGTH_LONG).show()
                        } else {
                            Log.w("TRIGGER", "No file path returned after stopping motion recording.")
                            savedFilePath.value = null
                            Toast.makeText(this, "No data file generated", Toast.LENGTH_LONG).show()
                        }

                        db.collection("triggers").document(deviceId)
                            .update(
                                mapOf(
                                    "motion_stop" to false,
                                    "recording" to false,
                                    "stopped" to true,
                                    "upload_complete" to true
                                )
                            )

                        motionSensorManager = null

                        lifecycleScope.launch {
                            delay(6000)
                            savedFilePath.value = null
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleJob?.cancel()
        scanJob?.cancel()
        if (::bleScanner.isInitialized) bleScanner.stopScanning()
        if (::bleAdvertiser.isInitialized) bleAdvertiser.stopAdvertising()
        motionSensorManager?.stop()
    }
}
