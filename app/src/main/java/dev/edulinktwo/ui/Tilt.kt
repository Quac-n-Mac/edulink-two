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
 * Perspective tilt, roughly [-1, 1] per axis over about +/-35 degrees.
 * Driven by the game rotation vector (gyro + accelerometer fusion, no
 * compass), so it tracks phone ROTATION fast like perspective wallpapers.
 * A slow-adapting reference recentres around however the phone is being
 * held, so the effect is about movement, not absolute angle.
 */
@Composable
fun rememberPerspective(): State<Offset> {
    val context = LocalContext.current.applicationContext
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        var refPitch = 0f
        var refRoll = 0f
        var sx = 0f
        var sy = 0f
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val pitch = ori[1]
                val roll = ori[2]
                if (!primed) {
                    refPitch = pitch; refRoll = roll
                    primed = true
                    return
                }
                // Very slow reference adaptation (~8s) so the effect
                // recentres around the current grip.
                refPitch += (pitch - refPitch) * 0.004f
                refRoll += (roll - refRoll) * 0.004f
                // Light smoothing (~150ms) keeps motion fluid, not jumpy.
                val a = 0.55f
                sx = a * sx + (1 - a) * ((roll - refRoll) / 0.6f)
                sy = a * sy + (1 - a) * ((pitch - refPitch) / 0.6f)
                tilt.value = Offset(sx.coerceIn(-1f, 1f), sy.coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

/** Back-compat alias used by the older glass experiments. */
@Composable
fun rememberTilt(): State<Offset> = rememberPerspective()
