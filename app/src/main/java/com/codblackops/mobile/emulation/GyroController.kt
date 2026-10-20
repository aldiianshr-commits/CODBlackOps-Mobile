package com.codblackops.mobile.emulation

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GyroProcessor

/** Feeds the device gyroscope into the [InputBridge] (gyro aiming). */
class GyroController(
    private val activity: Activity,
    private val bridge: InputBridge,
    settings: ControlSettings,
) : SensorEventListener {
    private val sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val processor = GyroProcessor().apply { configure(settings) }
    private var listening = false

    val available: Boolean get() = gyro != null

    fun start() {
        val s = gyro ?: return
        if (listening) return
        listening = sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
        bridge.onGyro(0f, 0f)
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            activity.windowManager.defaultDisplay.rotation
        }
        return when (r) {
            Surface.ROTATION_90 -> 1
            Surface.ROTATION_180 -> 2
            Surface.ROTATION_270 -> 3
            else -> 0
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        val r = processor.process(event.values[0], event.values[1], event.values[2], rotation())
        bridge.onGyro(r[0], r[1])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
