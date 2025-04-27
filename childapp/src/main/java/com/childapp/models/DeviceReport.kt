package com.childapp.models

data class DetectedDevice(
    val deviceId: String,
    val estimatedDistance: Double
)

data class DeviceReport(
    val myId: String,
    val timestamp: Long,
    val detectedDevices: List<DetectedDevice>
)
