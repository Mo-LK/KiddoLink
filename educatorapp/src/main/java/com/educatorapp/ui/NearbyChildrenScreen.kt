package com.educatorapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.educatorapp.ble.BleScanner
import com.educatorapp.models.PresenceReport

@Composable
fun NearbyChildrenScreen(
    scanner: BleScanner,
    reports: List<Map<String, Any>>,
    onTriggerToggle: (String) -> Unit,
    isRecordingMap: Map<String, Boolean>,
    uploadCompleteMap: Map<String, Boolean>,
    modifier: Modifier = Modifier
) {
    val devices = scanner.lastDetectedDevices
        .distinctBy { it.deviceId }
        .sortedBy { it.estimatedDistance }

    val presenceReports = reports.mapNotNull { report ->
        val id = report["deviceId"] as? String ?: return@mapNotNull null
        val time = report["time"] as? String ?: return@mapNotNull null
        val isAlone = report["isAlone"] as? Boolean ?: false
        PresenceReport(id, time, isAlone)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Nearby Children", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Text("No children detected.")
        } else {
            devices.forEach { device ->
                Text("• ${device.deviceId}: ~${"%.2f".format(device.estimatedDistance)} meters")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Presence Timeline", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        ChildPresenceTimeline(reports = presenceReports)

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Raw Data Preview (latest 3)", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        reports
            .sortedByDescending { it["time"] as? String ?: "" }
            .take(3)
            .forEach { report ->
                val id = report["deviceId"] as? String ?: "Unknown"
                val alone = report["isAlone"] as? Boolean ?: false
                val nearby = (report["nearbyIds"] as? List<*>)?.joinToString(", ") ?: "none"
                val time = report["time"] as? String ?: "unknown"

                Text("• $id @ $time")
                Text("  Alone: $alone | Nearby: $nearby", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
            }

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Trigger Motion Recording", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        devices.forEach { device ->
            val isRecording = isRecordingMap[device.deviceId] == true

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onTriggerToggle(device.deviceId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRecording) "Stop ${device.deviceId}" else "Trigger ${device.deviceId}")
                }

            }
        }
    }
}
