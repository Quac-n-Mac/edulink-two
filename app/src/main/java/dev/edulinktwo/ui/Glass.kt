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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
uniform float edgeWidth;     // rim band width, px
uniform float refrStrength;  // refraction offset at rim, px
uniform float blurRadius;
uniform float dispersion;    // chromatic spread factor
uniform float2 lightDir;     // normalized, toward the light
uniform float baseAlpha;
uniform float tintLighten;
uniform float3 stainColor;
uniform float stain;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 halfSize = resolution * 0.5;
    float2 p = fragCoord - halfSize;
    float d = sdRoundRect(p, halfSize, min(cornerRadius, min(halfSize.x, halfSize.y)));

    float rimT = smoothstep(-edgeWidth, 0.0, d);   // 0 deep inside -> 1 at edge
    float2 grad = normalize(p + float2(0.0001));
    float2 normal = grad * rimT;

    // refraction offsets, in backdrop-texture px
    // inward sampling: a convex lens magnifies, so nothing outside the
    // shape can ever appear inside it (hard containment at the rim)
    float2 off = -normal * refrStrength * glassScale;
    float2 base = (surfacePos + fragCoord) * glassScale;

    // chromatic dispersion: R and B refract more/less than G
    half rC = backdrop.eval(base + off * (1.0 + dispersion)).r;
    half bC = backdrop.eval(base + off * (1.0 - dispersion)).b;

    // slight blur on the green/base sample: 4-tap cross, tiny radius
    float2 t = float2(blurRadius);
    float2 g0 = base + off;
    half4 gSum = backdrop.eval(g0 + float2(t.x, 0.0)) + backdrop.eval(g0 - float2(t.x, 0.0))
               + backdrop.eval(g0 + float2(0.0, t.y)) + backdrop.eval(g0 - float2(0.0, t.y));
    half gC = gSum.g * 0.25;
    half aC = gSum.a * 0.25;

    half3 col = half3(rC, gC, bC);
    col = mix(col, half3(1.0), tintLighten);
    col *= half3(0.97, 0.985, 1.03);

    // regular-variant adaptivity (HIG/WWDC25 219): adjust luminosity of the
    // backdrop content to keep foreground legible - lift over dark content,
    // settle over light content.
    float luma = dot(float3(col), float3(0.299, 0.587, 0.114));
    float lift = (1.0 - smoothstep(0.12, 0.5, luma)) * 0.16;  // dark behind -> lift
    float settle = smoothstep(0.62, 0.95, luma) * 0.14;       // bright behind -> dim
    col = col * (1.0 - settle) + half3(1.0) * lift;

    // stained glass: a colored tint the light passes through
    col = mix(col, half3(stainColor), stain);

    // fresnel specular rim, brightest toward the light
    float rimBand = rimT * (1.0 - smoothstep(0.0, 1.5, d));
    float facing = max(dot(grad, lightDir), 0.0);
    col += rimBand * (0.18 + 0.6 * pow(facing, 2.5));

    // inner depth shading away from the light
    float inner = (1.0 - rimT) * smoothstep(-edgeWidth * 2.5, -edgeWidth * 0.4, d);
    col *= 1.0 - inner * max(dot(grad, -lightDir), 0.0) * 0.12;

    float alpha = (1.0 - smoothstep(-1.2, -0.2, d)) * baseAlpha * aC;
    return half4(col * alpha, alpha);
}
"""

class GlassBackdrop {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
    var scale by mutableStateOf(0.5f)
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
    scale: Float = 1.0f
): Modifier = this.onGloballyPositioned { backdrop.rootWinPos = it.positionInWindow() }.drawWithContent {
    if (!backdrop.captureInFlight && backdrop.capturedVersion < backdrop.captureVersion) {
        backdrop.captureInFlight = true
        // Capture and sampling both use full-resolution root pixels.
        layer.record(androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) {
            this@drawWithContent.drawContent()
        }
        val v = backdrop.captureVersion
        scope.launch {
            try {
            val bmp = layer.toImageBitmap()
            if (v.toLong() >= backdrop.capturedVersion) {
                backdrop.scale = 1f
                backdrop.bitmap = bmp
            }
            backdrop.capturedVersion = maxOf(backdrop.capturedVersion, v.toLong())
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
            val bmp = backdrop.bitmap ?: return@drawBehind
            shader.setInputBuffer(
                "backdrop",
                android.graphics.BitmapShader(
                    bmp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
                )
            )
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("surfacePos", pos.x, pos.y)
            shader.setFloatUniform("glassScale", backdrop.scale)
            shader.setFloatUniform("cornerRadius", cornerRadius.toPx())
            shader.setFloatUniform("edgeWidth", edgeWidth.toPx())
            shader.setFloatUniform("refrStrength", refrStrength.toPx() * VisualTuning.refraction)
            shader.setFloatUniform("dispersion", 0.45f)
            shader.setFloatUniform("blurRadius", blurRadius.toPx())
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
