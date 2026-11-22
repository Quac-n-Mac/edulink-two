package dev.edulinktwo.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Low-pass filtered gravity tilt, roughly [-1, 1] per axis. Powers the
 * parallax layer offsets and the glass shader's travelling light direction.
 * Slight on purpose: amplitudes are applied by the callers in single-digit dp.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current.applicationContext
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var fx = 0f
        var fy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // gravity ~9.81 on the axis pointing down; normalize and smooth
                val alpha = 0.88f
                fx = alpha * fx + (1 - alpha) * (e.values[0] / 9.81f)
                fy = alpha * fy + (1 - alpha) * (e.values[1] / 9.81f)
                tilt.value = Offset(fx.coerceIn(-1f, 1f), fy.coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}
