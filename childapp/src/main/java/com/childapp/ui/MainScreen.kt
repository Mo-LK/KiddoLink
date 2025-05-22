package com.childapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    deviceId: String,
    onScan: () -> Unit,
    nearbyDevices: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Device ID: $deviceId", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "BLE Scanning & Advertising Active...", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onScan) {
            Text("Scan Nearby Devices")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Nearby Devices:")
        nearbyDevices.forEach { deviceInfo ->
            Text(text = deviceInfo, style = MaterialTheme.typography.bodySmall)
        }
    }
}