package com.educatorapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.educatorapp.ble.BleScanner
import com.educatorapp.service.BleForegroundService
import com.educatorapp.ui.NearbyChildrenScreen
import com.educatorapp.ui.theme.KiddoLinkTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*

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
                val firestoreReports = remember { mutableStateListOf<Map<String, Any>>() }
                val recordingState = remember { mutableStateMapOf<String, Boolean>() }
                val uploadCompleteMap = remember { mutableStateMapOf<String, Boolean>() }

                LaunchedEffect(permissionsGranted) {
                    if (permissionsGranted && scannerState.value == null) {
                        startForegroundBleService()
                        scannerState.value = BleScanner(this@EducatorMainActivity).also {
                            it.startScanning()
                        }

                        FirebaseFirestore.getInstance()
                            .collection("presence_reports")
                            .addSnapshotListener { snapshot, _ ->
                                snapshot?.documents?.let { docs ->
                                    firestoreReports.clear()
                                    firestoreReports.addAll(docs.mapNotNull { it.data })
                                }
                            }

                        FirebaseFirestore.getInstance()
                            .collection("triggers")
                            .addSnapshotListener { snapshot, _ ->
                                snapshot?.documents?.forEach { doc ->
                                    val deviceId = doc.id
                                    val uploadComplete = doc.getBoolean("upload_complete") ?: false
                                    uploadCompleteMap[deviceId] = uploadComplete
                                }
                            }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val scanner = scannerState.value

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (permissionsGranted && scanner != null) {
                            NearbyChildrenScreen(
                                scanner = scanner,
                                reports = firestoreReports,
                                onTriggerToggle = { deviceId ->
                                    val db = FirebaseFirestore.getInstance()
                                    val ref = db.collection("triggers").document(deviceId)
                                    val isRecording = recordingState[deviceId] == true
                                    recordingState[deviceId] = !isRecording

                                    if (!isRecording) {
                                        ref.set(
                                            mapOf(
                                                "motion_triggered" to true,
                                                "upload_complete" to false,
                                                "motion_stop" to false,
                                                "recording" to false,
                                                "stopped" to false
                                            ),
                                            SetOptions.merge()
                                        )
                                    } else {
                                        ref.set(
                                            mapOf(
                                                "motion_stop" to true
                                            ),
                                            SetOptions.merge()
                                        )
                                    }
                                },
                                isRecordingMap = recordingState,
                                uploadCompleteMap = uploadCompleteMap
                            )
                        } else {
                            Text("Waiting for permissions", modifier = Modifier.fillMaxWidth())
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { writeTestDataFromTenToEleven() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Insert 10–11 AM Test Data")
                        }
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

    private fun writeTestDataFromTenToEleven() {
        val db = FirebaseFirestore.getInstance()
        val children = listOf("C-A", "C-B", "C-C")
        val start = 10 * 60
        val end = 11 * 60
        val delayBetweenWrites = 100L

        CoroutineScope(Dispatchers.IO).launch {
            for (minute in start..end step 5) {
                val time = "%02d:%02d".format(minute / 60, minute % 60)
                for (child in children) {
                    val isAlone = (0..100).random() < 30
                    val nearbyIds = if (isAlone) emptyList<String>() else children.filter { it != child }.shuffled().take((1..2).random())

                    val doc = mapOf(
                        "deviceId" to child,
                        "time" to time,
                        "isAlone" to isAlone,
                        "nearbyIds" to nearbyIds
                    )

                    db.collection("presence_reports").add(doc)
                    delay(delayBetweenWrites)
                }
            }
        }
    }
}
