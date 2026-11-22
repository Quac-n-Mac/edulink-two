package dev.edulinktwo.ui

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Liquid glass, iOS 26 style, hand-rolled (no libraries): an AGSL lens
 * shader doing real backdrop refraction, visible chromatic dispersion at
 * the rim, fresnel specular highlights and inner depth shading. Blur is
 * deliberately slight - the glassiness is the effect, not the blur.
 *
 * API 33+ : full lens shader over a real backdrop texture.
 * API 31-32: frosted fallback (translucency + rim highlight, no refraction).
 * API <31 : static frosted fallback.
 *
 * Android exposes no live "pixels behind this view", so the glass samples a
 * backdrop texture we own: the root content is recorded into a downscaled
 * GraphicsLayer texture, refreshed when content changes (bump
 * [GlassBackdrop.captureVersion]); during scroll the sampling origin tracks
 * the scroll offset so the refraction stays glued to the content.
 */
internal const val GLASS_AGSL = """
uniform shader backdrop;
uniform float2 resolution;   // surface size, px
uniform float2 surfacePos;   // surface top-left in root content, px
uniform float glassScale;    // backdrop px per root px
uniform float cornerRadius;
uniform float edgeWidth;     // lens thickness: depth at which refraction dies, px
uniform float refrStrength;  // refraction distance at the rim, px
uniform float dispersion;    // relative IOR spread across R/B
uniform float2 lightDir;     // normalized, toward the light
uniform float baseAlpha;
uniform float tintLighten;
uniform float3 stainColor;
uniform float stain;

// Liquid-glass refraction modelled on a spherical-cap lens through Snell's
// law (model: iyinchao/liquid-glass-studio, MIT): the displacement profile
// comes from the actual optics, not a hand-shaped falloff, which is what
// makes the rim read as bent light rather than a smear.
const float IOR = 1.45;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// True SDF-gradient normal: perpendicular to the edge everywhere, unlike a
// radial normalize(p) which points diagonally along straight edges.
float2 sdfNormal(float2 p, float2 halfSize, float r) {
    float e = 0.75;
    float2 g = float2(
        sdRoundRect(p + float2(e, 0.0), halfSize, r) - sdRoundRect(p - float2(e, 0.0), halfSize, r),
        sdRoundRect(p + float2(0.0, e), halfSize, r) - sdRoundRect(p - float2(0.0, e), halfSize, r));
    float l = length(g);
    return l > 0.0001 ? g / l : float2(0.0, -1.0);
}

// Snell displacement magnitude at a given depth inside the lens.
float edgeFactor(float depth, float thickness, float ior) {
    if (depth >= thickness) return 0.0;
    float x = 1.0 - depth / thickness;
    float thetaI = asin(clamp(x * x, 0.0, 1.0));
    float thetaT = asin(clamp(sin(thetaI) / ior, -1.0, 1.0));
    return -tan(thetaT - thetaI);
}

half4 main(float2 fragCoord) {
    float2 halfSize = resolution * 0.5;
    float2 p = fragCoord - halfSize;
    float cr = min(cornerRadius, min(halfSize.x, halfSize.y));
    float d = sdRoundRect(p, halfSize, cr);
    float depth = -d;                       // 0 at rim, grows inward
    float thickness = max(edgeWidth, 1.0);
    float2 n = sdfNormal(p, halfSize, cr);
    float2 base = (surfacePos + fragCoord) * glassScale;

    // Per-channel refraction: slight IOR spread = chromatic dispersion.
    // Inward sampling: a convex lens magnifies, so nothing outside the
    // shape can ever appear inside it.
    float fR = edgeFactor(depth, thickness, IOR * (1.0 - dispersion));
    float fG = edgeFactor(depth, thickness, IOR);
    float fB = edgeFactor(depth, thickness, IOR * (1.0 + dispersion));
    float2 rd = -n * refrStrength * glassScale;
    half3 col = half3(
        backdrop.eval(base + rd * fR).r,
        backdrop.eval(base + rd * fG).g,
        backdrop.eval(base + rd * fB).b);
    half aC = backdrop.eval(base).a;

    col = mix(col, half3(1.0), tintLighten);
    col *= half3(0.97, 0.985, 1.03);

    // regular-variant adaptivity: lift over dark content, settle over light
    float luma = dot(float3(col), float3(0.299, 0.587, 0.114));
    float lift = (1.0 - smoothstep(0.12, 0.5, luma)) * 0.16;
    float settle = smoothstep(0.62, 0.95, luma) * 0.14;
    col = col * (1.0 - settle) + half3(1.0) * lift;

    // stained glass: a colored tint the light passes through
    col = mix(col, half3(stainColor), stain);

    // fresnel rim: tight pow profile, brightest toward the light
    float rimT = 1.0 - clamp(depth / thickness, 0.0, 1.0);
    float facing = max(dot(n, lightDir), 0.0);
    col += pow(rimT, 3.0) * (0.10 + 0.55 * pow(facing, 2.5));

    // inner depth shading away from the light
    col *= 1.0 - pow(rimT, 2.0) * max(dot(n, -lightDir), 0.0) * 0.10;

    float alpha = (1.0 - smoothstep(-1.2, -0.2, d)) * baseAlpha * aC;
    return half4(col * alpha, alpha);
}
"""

class GlassBackdrop {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
    var scale by mutableStateOf(0.5f)
    /** Downscaled copy of the capture: sampling it bilinearly IS the blur,
     * so bars get real frost without multi-tap ghosting. */
    var frost by mutableStateOf<ImageBitmap?>(null)
    var frostScale by mutableStateOf(1f)
    /** window position of the captured root's top-left, px */
    var rootWinPos by mutableStateOf(Offset.Zero)
    /** bump to request a fresh capture */
    var captureVersion by mutableIntStateOf(0)
    internal var capturedVersion by mutableLongStateOf(-1)
    internal var captureInFlight = false
}

/**
 * Root content modifier: draws normally; records the content into a
 * downscaled texture whenever [backdrop].captureVersion advances (and once
 * on first frame). Cheap: one reduced-scale record + bitmap copy per change.
 */
fun Modifier.glassBackdrop(
    backdrop: GlassBackdrop,
    layer: GraphicsLayer,
    scope: CoroutineScope,
    scale: Float = 0.5f
): Modifier = this.onGloballyPositioned { backdrop.rootWinPos = it.positionInWindow() }.drawWithContent {
    if (!backdrop.captureInFlight && backdrop.capturedVersion < backdrop.captureVersion) {
        backdrop.captureInFlight = true
        // Record at `scale`: a quarter of the pixels to read back, and
        // smaller textures stay well clear of GPU/driver size quirks.
        val cw = (size.width * scale).roundToInt().coerceAtLeast(1)
        val ch = (size.height * scale).roundToInt().coerceAtLeast(1)
        layer.record(androidx.compose.ui.unit.IntSize(cw, ch)) {
            scale(scale, scale, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
        }
        val v = backdrop.captureVersion
        scope.launch {
            try {
                // toImageBitmap SHARES the layer's GPU buffer: the next
                // record() overwrites it and shaders then sample torn
                // garbage (magenta/green noise). Detach into a plain
                // software bitmap before anyone samples it.
                val hw = layer.toImageBitmap().asAndroidBitmap()
                val safe = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                if (safe != null && v.toLong() >= backdrop.capturedVersion) {
                    // Bake the blur into real pixels: downscale hard, then
                    // upscale bilinearly. Smooth under ANY filter mode - the
                    // BitmapShader ctor that controls filtering is API 34+.
                    val fw = (safe.width / 4).coerceAtLeast(1)
                    val fh = (safe.height / 4).coerceAtLeast(1)
                    val small = android.graphics.Bitmap.createScaledBitmap(safe, fw, fh, true)
                    backdrop.frost = android.graphics.Bitmap.createScaledBitmap(small, safe.width, safe.height, true).asImageBitmap()
                    backdrop.frostScale = scale
                    backdrop.bitmap = safe.asImageBitmap()
                    backdrop.scale = scale
                }
                backdrop.capturedVersion = maxOf(backdrop.capturedVersion, v.toLong())
            } catch (_: Throwable) {
                // keep the last good capture; never poison the textures
            } finally {
                backdrop.captureInFlight = false
            }
        }
    }
    drawContent()
}

fun Modifier.liquidGlass(
    backdrop: GlassBackdrop,
    cornerRadius: Dp = 24.dp,
    edgeWidth: Dp = 10.dp,
    refrStrength: Dp = 9.dp,
    blurRadius: Dp = 1.4.dp,
    baseAlpha: Float = 0.55f,
    stainColor: Color = Color.Transparent,
    stain: Float = 0f,
    frosted: Boolean = false,
    lightDir: () -> Offset = { Offset(-0.55f, -0.83f) },
): Modifier {
    // Frosted fallback when there is no usable backdrop (old API or no
    // capture yet): near-invisible wash + a faint rim, never a gray blob.
    val fallback: Modifier.() -> Modifier = {
        drawBehind {
            val rr = RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerRadius.toPx()))
            val path = Path().apply { addRoundRect(rr) }
            clipPath(path) { drawRect(Color.White.copy(alpha = (0.06f * VisualTuning.glassAlpha).coerceIn(0f, 1f))) }
            drawRoundRect(
                color = Color(0x2EFFFFFF),
                size = size,
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
    if (Build.VERSION.SDK_INT < 33 || !VisualTuning.realGlass) {
        return this.fallback()
    }
    var pos by mutableStateOf(Offset.Zero)
    val shader = RuntimeShader(GLASS_AGSL)
    val brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
    }
    return this
        .onGloballyPositioned { pos = it.positionInWindow() - backdrop.rootWinPos }
        .drawBehind {
            val sharp = backdrop.bitmap ?: return@drawBehind
            val useFrost = frosted && backdrop.frost != null
            val bmp = if (useFrost) backdrop.frost!! else sharp
            val texScale = if (useFrost) backdrop.frostScale else backdrop.scale
            // The frost texture has its blur baked into real pixels (see
            // the capture), so shader filtering no longer matters.
            shader.setInputBuffer(
                "backdrop",
                android.graphics.BitmapShader(
                    bmp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
                )
            )
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("surfacePos", pos.x, pos.y)
            shader.setFloatUniform("glassScale", texScale)
            shader.setFloatUniform("cornerRadius", cornerRadius.toPx())
            shader.setFloatUniform("edgeWidth", edgeWidth.toPx() * 2.5f)
            shader.setFloatUniform("refrStrength", refrStrength.toPx() * VisualTuning.refraction)
            shader.setFloatUniform("dispersion", 0.04f)
            val ld = lightDir()
            shader.setFloatUniform("lightDir", ld.x, ld.y)
            shader.setFloatUniform("baseAlpha", (baseAlpha * VisualTuning.glassAlpha).coerceIn(0f, 1f))
            shader.setFloatUniform("tintLighten", 0.07f)
            shader.setFloatUniform("stainColor", stainColor.red, stainColor.green, stainColor.blue)
            shader.setFloatUniform("stain", stain)
            val rr = RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerRadius.toPx()))
            val path = Path().apply { addRoundRect(rr) }
            clipPath(path) { drawRect(brush) }
        }
}
