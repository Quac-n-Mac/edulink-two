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

private data class Bubble(val id: Int, val pos: Offset, val stretch: Float = 0f, val angle: Float = 0f)

/** Observes without consuming input. Taps AND drags leave fading bubbles:
 * one where the finger lands, then a trail that follows every drag. */
@Composable
fun TapFeedbackHost(content: @Composable () -> Unit) {
    val backdrop = remember { GlassBackdrop() }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var bubbles by remember { mutableStateOf(listOf<Bubble>()) }
    var nextId by remember { mutableIntStateOf(0) }
    val radius = with(LocalDensity.current) { 24.dp.toPx() }
    val trailStep = with(LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (VisualTuning.tapFeedback) {
                bubbles = bubbles + Bubble(nextId, down.position)
                nextId++
                backdrop.captureVersion++
            }
            var lastSpawn = down.position
            var active = true
            while (active) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                if (finger.pressed) {
                    if (VisualTuning.tapFeedback && (finger.position - lastSpawn).getDistance() > trailStep) {
                        val ch = finger.positionChange()
                        val sp = kotlin.math.sqrt(ch.x * ch.x + ch.y * ch.y)
                        val amt = (sp / 30f).coerceIn(0f, 1f)
                        val ang = if (amt > 0f) (kotlin.math.atan2(ch.y, ch.x) * 180f / kotlin.math.PI).toFloat() else 0f
                        bubbles = bubbles + Bubble(nextId, finger.position, amt, ang)
                        nextId++
                        lastSpawn = finger.position
                    }
                } else active = false
            }
        }
    }) {
        Box(Modifier.fillMaxSize().glassBackdrop(backdrop, layer, scope)) { content() }
        bubbles.forEach { b ->
            key(b.id) {
                val alpha = remember { Animatable(1f) }
                val relax = remember { Animatable(1f) }
                LaunchedEffect(Unit) {
                    alpha.animateTo(0f, tween(if (VisualTuning.systemReducedMotion) 0 else 220))
                    bubbles = bubbles.filter { it.id != b.id }
                }
                LaunchedEffect(Unit) { relax.animateTo(0f, tween(200)) }
                Box(Modifier.offset { IntOffset((b.pos.x - radius).roundToInt(), (b.pos.y - radius).roundToInt()) }
                    .size(48.dp)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        val s = b.stretch * relax.value
                        if (s > 0f) {
                            scaleX = 1f + 0.7f * s
                            scaleY = 1f - 0.3f * s
                            rotationZ = b.angle
                        }
                    }
                    .liquidGlass(backdrop, cornerRadius = 24.dp, edgeWidth = 7.dp,
                        refrStrength = 5.dp, baseAlpha = 0.55f, stainColor = Color.White, stain = 0.3f))
            }
        }
    }
}
