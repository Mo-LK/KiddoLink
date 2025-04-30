package com.educatorapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.educatorapp.models.DetectedDevice
import com.educatorapp.utils.DistanceEstimator
import java.util.*

@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    val lastDetectedDevices = mutableStateListOf<DetectedDevice>()

    private val stableDistances = mutableMapOf<String, Double>()
    private val suspectCounters = mutableMapOf<String, Int>()
    private val MAX_SUSPECT_BEFORE_ACCEPT = 2
    private val DISTANCE_VARIATION_THRESHOLD = 0.5

    private val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord ?: return
            val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
            val id = serviceData?.toString(Charsets.UTF_8) ?: return

            if (!id.startsWith("C-") && id != "EDU") return

            val newDistance = DistanceEstimator.estimateDistance(result.rssi)

            val stable = stableDistances[id]
            if (stable == null) {
                stableDistances[id] = newDistance
                suspectCounters[id] = 0
                addOrUpdateDevice(id, newDistance)
                return
            }

            val variation = kotlin.math.abs(newDistance - stable) / stable
            if (variation > DISTANCE_VARIATION_THRESHOLD) {
                val count = suspectCounters.getOrDefault(id, 0) + 1
                if (count >= MAX_SUSPECT_BEFORE_ACCEPT) {
                    stableDistances[id] = newDistance
                    suspectCounters[id] = 0
                    addOrUpdateDevice(id, newDistance)
                } else {
                    suspectCounters[id] = count
                    Log.d("BLE_FILTER", "Ignoring suspect distance for $id: $newDistance (count=$count)")
                }
            } else {
                stableDistances[id] = newDistance
                suspectCounters[id] = 0
                addOrUpdateDevice(id, newDistance)
            }
        }

        private fun addOrUpdateDevice(id: String, distance: Double) {
            val detectedDevice = DetectedDevice(deviceId = id, estimatedDistance = distance)
            val index = lastDetectedDevices.indexOfFirst { it.deviceId == id }

            if (index >= 0) {
                lastDetectedDevices[index] = detectedDevice
            } else {
                lastDetectedDevices.add(detectedDevice)
            }

            Log.d("BLE_SCAN", "Detected $id at ~${"%.2f".format(distance)} meters")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_SCAN", "Scan failed with error: $errorCode")
        }
    }

    fun startScanning() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf<ScanFilter>()

        scanner?.startScan(filters, settings, scanCallback)
        Log.d("BLE_SCAN", "Started BLE scanning")
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        Log.d("BLE_SCAN", "Stopped BLE scanning")
    }
}
