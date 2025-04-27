package com.childapp.ble

package com.childapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.childapp.models.DetectedDevice
import com.childapp.utils.DistanceEstimator

@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    var lastDetectedDevices = mutableListOf<DetectedDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.name?.let { deviceName ->
                val rssi = result.rssi
                val distance = DistanceEstimator.estimateDistance(rssi)

                val detectedDevice = DetectedDevice(
                    deviceId = deviceName,
                    estimatedDistance = distance
                )

                lastDetectedDevices.add(detectedDevice)

                Log.d("BLE_SCAN", "Detected ${deviceName} at ~${"%.2f".format(distance)} meters")
            }
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
