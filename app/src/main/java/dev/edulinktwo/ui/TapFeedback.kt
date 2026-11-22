package dev.edulinktwo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/** Observes without consuming input. Only completed taps produce a bubble. */
@Composable
fun TapFeedbackHost(content: @Composable () -> Unit) {
    val backdrop = remember { GlassBackdrop() }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var point by remember { mutableStateOf<Offset?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }
    val radius = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(serial) {
        if (serial > 0) {
            alpha.snapTo(1f)
            alpha.animateTo(0f, tween(200))
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dragged = false
            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val finger = event.changes.firstOrNull { it.id == down.id }
                if (finger == null) break
                if ((finger.position - down.position).getDistance() > viewConfiguration.touchSlop || event.changes.size > 1) dragged = true
                if (!finger.pressed) {
                    if (!dragged && VisualTuning.tapFeedback) {
                        point = finger.position
                        backdrop.captureVersion++
                        serial++
                    }
                    finished = true
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize().glassBackdrop(backdrop, layer, scope)) { content() }
        point?.let { p ->
            Box(Modifier.offset { IntOffset((p.x - radius).roundToInt(), (p.y - radius).roundToInt()) }
                .size(48.dp)
                .graphicsLayer { this.alpha = alpha.value }
                .liquidGlass(backdrop, cornerRadius = 24.dp, edgeWidth = 7.dp,
                    refrStrength = 5.dp, baseAlpha = 0.55f, stainColor = Color.White, stain = 0.3f))
        }
    }
}
