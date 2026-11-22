package dev.edulinktwo.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** All values are live state. Changes persist locally on this device. */
object VisualTuning {
    var systemReducedMotion by mutableStateOf(false)
    val motionStrength: Float get() = if (systemReducedMotion) 0f else parallax
    var parallax by mutableFloatStateOf(1f)
    var animationMs by mutableFloatStateOf(450f)
    var refraction by mutableFloatStateOf(1f)
    var glassAlpha by mutableFloatStateOf(1f)
    var realGlass by mutableStateOf(true)
    var tapFeedback by mutableStateOf(true)
    var sheetBlur by mutableFloatStateOf(20f)
    fun load(context: Context) {
        val p = context.getSharedPreferences("visual-tuning", Context.MODE_PRIVATE)
        parallax = p.getFloat("parallax", 1f).coerceIn(0f, 3f)
        animationMs = p.getFloat("animationMs", 450f).coerceIn(100f, 1500f)
        refraction = p.getFloat("refraction", 1f).coerceIn(0f, 3f)
        glassAlpha = p.getFloat("glassAlpha", 1f).coerceIn(0.1f, 2f)
        realGlass = p.getBoolean("realGlass", true)
        tapFeedback = p.getBoolean("tapFeedback", true)
        sheetBlur = p.getFloat("sheetBlur", 20f).coerceIn(0f, 50f)
    }
    fun save(context: Context) {
        context.getSharedPreferences("visual-tuning", Context.MODE_PRIVATE).edit()
            .putFloat("parallax", parallax).putFloat("animationMs", animationMs)
            .putFloat("refraction", refraction).putFloat("glassAlpha", glassAlpha)
            .putBoolean("realGlass", realGlass).putBoolean("tapFeedback", tapFeedback)
            .putFloat("sheetBlur", sheetBlur).apply()
    }
    fun reset() {
        parallax = 1f; animationMs = 450f; refraction = 1f; glassAlpha = 1f
        realGlass = true; tapFeedback = true; sheetBlur = 20f
    }
}

@Composable
fun CustomisationSection() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (VisualTuning.systemReducedMotion) Text("System animations are disabled. Parallax and navigation animation are reduced automatically.")
        Text("Changes apply now and are saved on this device. Screenshot these values when you have your favourite setup.")
        TuningSlider("Parallax", VisualTuning.parallax, 0f..3f, "%.2fx".format(VisualTuning.parallax)) { VisualTuning.parallax = it }
        TuningSlider("Animation duration", VisualTuning.animationMs, 100f..1500f, "%.0f ms".format(VisualTuning.animationMs)) { VisualTuning.animationMs = it }
        TuningSlider("Refraction strength", VisualTuning.refraction, 0f..3f, "%.2fx".format(VisualTuning.refraction)) { VisualTuning.refraction = it }
        TuningSlider("Glass opacity", VisualTuning.glassAlpha, 0.1f..2f, "%.2fx".format(VisualTuning.glassAlpha)) { VisualTuning.glassAlpha = it }
        TuningSlider("Sheet background blur", VisualTuning.sheetBlur, 0f..50f, "%.0f px".format(VisualTuning.sheetBlur)) { VisualTuning.sheetBlur = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Real refractive glass")
            Switch(VisualTuning.realGlass, { VisualTuning.realGlass = it; VisualTuning.save(context) })
        }
        Text(if (VisualTuning.realGlass) "Samples the screen behind each glass surface." else "Simple translucent glass, no refraction.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tap bubbles")
            Switch(VisualTuning.tapFeedback, { VisualTuning.tapFeedback = it; VisualTuning.save(context) })
        }
        OutlinedButton(onClick = { VisualTuning.reset(); VisualTuning.save(context) }) { Text("Reset defaults") }
    }
}

@Composable
private fun TuningSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, change: (Float) -> Unit) {
    val context = LocalContext.current
    Column {
        Text("$name: $display")
        Slider(value, onValueChange = change, valueRange = range, onValueChangeFinished = { VisualTuning.save(context) })
    }
}

@Composable
fun PaintbrushIcon() {
    val color = LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        drawLine(color, androidx.compose.ui.geometry.Offset(11f * unit, 13f * unit),
            androidx.compose.ui.geometry.Offset(21f * unit, 3f * unit),
            strokeWidth = 5f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val bristles = androidx.compose.ui.graphics.Path().apply {
            moveTo(10f * unit, 14f * unit)
            cubicTo(4f * unit, 12f * unit, 6f * unit, 20f * unit, 2f * unit, 21f * unit)
            cubicTo(9f * unit, 23f * unit, 15f * unit, 19f * unit, 10f * unit, 14f * unit)
            close()
        }
        drawPath(bristles, color)
    }
}
