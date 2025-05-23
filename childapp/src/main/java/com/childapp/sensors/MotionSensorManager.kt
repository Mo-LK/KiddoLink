package com.childapp.sensors

import android.content.Context
import android.hardware.*
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MotionSensorManager(
    private val context: Context,
    private val deviceId: String
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var writer: FileWriter? = null
    private var listener: SensorEventListener? = null
    private lateinit var file: File
    private var savedPath: String? = null
    private var isRecording = false

    fun start(onFinished: (() -> Unit)? = null) {
        writer = null
        savedPath = null
        setupWriter()

        isRecording = true
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (!isRecording || writer == null || event == null) return

                try {
                    val ts = System.currentTimeMillis()
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val line = "$ts,$x,$y,$z\n"
                    writer?.write(line)
                } catch (e: IOException) {
                    Log.e("MOTION", "Write failed: ${e.message}")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onFinished?.invoke()
    }

    private fun setupWriter() {
        val dir = File(context.getExternalFilesDir(null), "motion_data")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        file = File(dir, "${deviceId}_$timestamp.csv")

        writer = try {
            FileWriter(file, true).also {
                savedPath = file.absolutePath
                Log.d("MOTION", "Writer initialized at $savedPath")
            }
        } catch (e: IOException) {
            Log.e("MOTION", "Failed to create FileWriter", e)
            null
        }
    }

    fun stop(onFinished: (() -> Unit)? = null) {
        isRecording = false
        listener?.let { sensorManager.unregisterListener(it) }

        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e("MOTION", "Failed to close writer", e)
        }

        if (::file.isInitialized && file.exists()) {
            savedPath = file.absolutePath
            Log.d("MOTION", "CSV file saved locally: $savedPath")
        } else {
            Log.w("MOTION", "CSV file not found or uninitialized")
            savedPath = null
        }

        onFinished?.invoke()
    }

    fun stopAndGetPath(onFinished: (String?) -> Unit) {
        stop {
            onFinished(savedPath)
        }
    }
}
