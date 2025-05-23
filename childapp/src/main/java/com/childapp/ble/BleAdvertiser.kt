package com.childapp.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.*

class BleAdvertiser(
    private val context: Context,
    private val myId: String
) {
    private var advertiser: BluetoothLeAdvertiser? = null

    fun startAdvertising() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (!adapter.isEnabled || !adapter.isMultipleAdvertisementSupported) {
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser

        val payload = myId

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), payload.toByteArray(Charset.forName("UTF-8")))
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BLE_ADVERTISER", "Permission denied for BLE advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e("BLE_ADVERTISER", "Unexpected error: ${e.message}")
        }
    }

    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            Log.d("BLE_ADVERTISER", "Stopped advertising")
        } catch (e: SecurityException) {
            Log.e("BLE_ADVERTISER", "Permission denied to stop advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e("BLE_ADVERTISER", "Unexpected error while stopping advertising: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLE_ADVERTISER", "Advertise started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_ADVERTISER", "Advertise failed: $errorCode")
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    }
}
