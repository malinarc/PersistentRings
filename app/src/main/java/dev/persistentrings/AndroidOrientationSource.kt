package dev.persistentrings

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

internal class AndroidOrientationSource(context: Context) : OrientationSource {
    private val manager = context.getSystemService(SensorManager::class.java)
    // Wake-up delivery takes priority for screen-off detection, then gravity over accelerometer.
    private val sensors = listOfNotNull(
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY, true),
        manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true),
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY, false),
        manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)
    ).distinct()
    override val available = sensors.isNotEmpty()
    override val hasWakeUpSensor = sensors.any { it.isWakeUpSensor }

    override fun register(sample: (OrientationSample) -> Unit): AutoCloseable? {
        val manager = manager ?: return null
        var registered = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!registered) return
                val (x, y, z) = event.values
                sample(OrientationSample(x.toDouble(), y.toDouble(), z.toDouble(),
                    event.timestamp / 1_000_000, event.sensor.type == Sensor.TYPE_ACCELEROMETER))
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val handler = Handler(Looper.getMainLooper())
        val started = sensors.any { sensor ->
            runCatching { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler) }
                .getOrDefault(false)
        }
        if (!started) return null
        return AutoCloseable {
            registered = false
            manager.unregisterListener(listener)
        }
    }
}
