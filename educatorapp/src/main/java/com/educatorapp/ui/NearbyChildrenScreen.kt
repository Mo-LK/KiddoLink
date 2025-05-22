package com.educatorapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.educatorapp.ble.BleScanner

@Composable
fun NearbyChildrenScreen(
    scanner: BleScanner,
    reports: List<Map<String, Any>>,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        scanner.startScanning()
    }

    val devices = scanner.lastDetectedDevices
        .distinctBy { it.deviceId }
        .sortedBy { it.estimatedDistance }

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

        Text("Latest Presence Reports", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        reports
            .sortedBy { it["time"] as? String ?: "" }
            .take(10)
            .forEach { report ->
                val id = report["deviceId"] as? String ?: "Unknown"
                val alone = report["isAlone"] as? Boolean ?: false
                val nearby = (report["nearbyIds"] as? List<*>)?.joinToString(", ") ?: "none"
                val time = report["time"] as? String ?: "unknown"

                Text("• $id @ $time")
                Text("  Alone: $alone | Nearby: $nearby", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
            }
    }
}
