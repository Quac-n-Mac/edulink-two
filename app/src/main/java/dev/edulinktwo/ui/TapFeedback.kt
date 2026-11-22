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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

private data class Bubble(val id: Int, val pos: Offset, val stretch: Float = 0f, val angle: Float = 0f)

/** Observes without consuming input. A tap leaves one fading bubble; a drag
 * is ONE glass blob glued to the finger that stretches along the motion and
 * relaxes when the finger pauses, fading out where the finger lifts. While
 * the nav bar is being dragged the blob stays off (the pill owns that drag). */
@Composable
fun TapFeedbackHost(content: @Composable () -> Unit) {
    val backdrop = remember { GlassBackdrop() }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var bubbles by remember { mutableStateOf(listOf<Bubble>()) }
    var nextId by remember { mutableIntStateOf(0) }
    val radius = with(LocalDensity.current) { 24.dp.toPx() }

    // The one live drag blob.
    var blobPos by remember { mutableStateOf<Offset?>(null) }
    var blobStretch by remember { mutableFloatStateOf(0f) }
    var blobAngle by remember { mutableFloatStateOf(0f) }
    // Jelly: the stretch relaxes every frame the finger doesn't add to it.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            blobStretch *= 0.86f
        }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (VisualTuning.tapFeedback && !VisualTuning.navBarDragging) {
                // No capture bump: bubbles live outside the captured content,
                // so spawning one must not trigger a full-screen readback.
                bubbles = (bubbles + Bubble(nextId, down.position)).takeLast(8)
                nextId++
            }
            var dragging = false
            var last = down.position
            var active = true
            while (active) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                if (finger.pressed) {
                    val dx = finger.position.x - finger.previousPosition.x
                    val dy = finger.position.y - finger.previousPosition.y
                    val sp = kotlin.math.hypot(dx, dy)
                    if (VisualTuning.tapFeedback && !VisualTuning.navBarDragging) {
                        if (dragging || (finger.position - down.position).getDistance() > 14f) {
                            dragging = true
                            blobPos = finger.position
                            if (sp > 0.5f) {
                                blobStretch = blobStretch * 0.55f + (sp / 30f).coerceIn(0f, 1f) * 0.45f
                                blobAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            }
                        }
                    }
                    last = finger.position
                } else {
                    // Finger up: the blob lets go and fades where it is.
                    if (dragging && VisualTuning.tapFeedback) {
                        bubbles = (bubbles + Bubble(nextId, last, blobStretch, blobAngle)).takeLast(8)
                        nextId++
                    }
                    dragging = false
                    blobPos = null
                    blobStretch = 0f
                    active = false
                }
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
                        refrStrength = 5.dp, baseAlpha = 0.55f, stainColor = Color.White, stain = 0.3f, frosted = true))
            }
        }
        val blob = blobPos
        if (blob != null && VisualTuning.tapFeedback) {
            Box(Modifier.offset { IntOffset((blob.x - radius).roundToInt(), (blob.y - radius).roundToInt()) }
                .size(48.dp)
                .graphicsLayer {
                    val s = blobStretch
                    scaleX = 1f + 0.9f * s
                    scaleY = 1f - 0.35f * s
                    rotationZ = blobAngle
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .liquidGlass(backdrop, cornerRadius = 24.dp, edgeWidth = 7.dp,
                    refrStrength = 6.dp, baseAlpha = 0.5f, stainColor = Color.White, stain = 0.3f, frosted = true))
        }
    }
}
