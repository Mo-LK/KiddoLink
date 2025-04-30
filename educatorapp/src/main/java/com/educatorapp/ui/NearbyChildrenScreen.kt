package com.educatorapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.educatorapp.ble.BleScanner

@Composable
fun NearbyChildrenScreen(scanner: BleScanner, modifier: Modifier = Modifier) {
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
    }
}
