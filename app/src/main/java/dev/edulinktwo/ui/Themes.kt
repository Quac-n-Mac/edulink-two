package dev.edulinktwo.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** A named colour identity: background gradient, bar tint and accent. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val bgTop: Color,
    val bgBottom: Color,
    val bar: Color,
    val accent: Color,
    val dark: Boolean
)

object ThemeStore {
    // His spec: Classic (the current look), Dark, and 8 others.
    val presets = listOf(
        ThemeSpec("classic", "Classic", Color(0xFFF6F8FA), Color(0xFFE1E8F0), Color(0xFFFFFFFF), Color(0xFF00365D), false),
        ThemeSpec("dark", "Dark", Color(0xFF0B1220), Color(0xFF16233A), Color(0xFF2E4A73), Color(0xFF4CA1E3), true),
        ThemeSpec("lightblue", "Light blue", Color(0xFFE8F4F8), Color(0xFFCDE5EE), Color(0xFF1F6F8B), Color(0xFF126487), false),
        ThemeSpec("deepblue", "Deep blue", Color(0xFF06202B), Color(0xFF0A3343), Color(0xFF2E6B82), Color(0xFF4DD0E1), true),
        ThemeSpec("forestgreen", "Forest green", Color(0xFFEAF4E7), Color(0xFFD3E8CD), Color(0xFF2E7D32), Color(0xFF1B5E20), false),
        ThemeSpec("darkgreen", "Dark green", Color(0xFF0C1E13), Color(0xFF14301F), Color(0xFF3E7C53), Color(0xFF66BB6A), true),
        ThemeSpec("orange", "Orange", Color(0xFFFDF1E5), Color(0xFFF9DEC7), Color(0xFFC05621), Color(0xFFE65100), false),
        ThemeSpec("purple", "Purple", Color(0xFF221425), Color(0xFF3A1F38), Color(0xFF7A4A78), Color(0xFFCE93D8), true),
        ThemeSpec("pink", "Pink", Color(0xFFFBEAED), Color(0xFFF6D4DB), Color(0xFFAD1457), Color(0xFFC2185B), false),
        ThemeSpec("darkgray", "Dark gray", Color(0xFF263238), Color(0xFF37474F), Color(0xFF607D8B), Color(0xFF90A4AE), true),
    )

    var current by mutableStateOf(presets[0]); private set

    fun load(context: Context) {
        val p = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
        val id = p.getString("id", "classic") ?: "classic"
        current = if (id == "custom" && p.contains("bgTop")) {
            ThemeSpec("custom", "Custom",
                Color(p.getInt("bgTop", 0)), Color(p.getInt("bgBottom", 0)),
                Color(p.getInt("bar", 0)), Color(p.getInt("accent", 0)),
                p.getBoolean("dark", false))
        } else presets.firstOrNull { it.id == id } ?: presets[0]
    }

    fun apply(context: Context, t: ThemeSpec) {
        current = t
        context.getSharedPreferences("theme", Context.MODE_PRIVATE).edit()
            .putString("id", t.id)
            .putInt("bgTop", t.bgTop.toArgb()).putInt("bgBottom", t.bgBottom.toArgb())
            .putInt("bar", t.bar.toArgb()).putInt("accent", t.accent.toArgb())
            .putBoolean("dark", t.dark).apply()
    }

    fun applyCustom(context: Context, base: Color, bar: Color, dark: Boolean) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        val bottom = shadeOf(base, if (dark) 0.07f else -0.07f)
        val accentL = if (dark) hsl[2].coerceAtLeast(0.55f) else hsl[2].coerceAtMost(0.40f)
        val accent = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.25f), accentL)))
        apply(context, ThemeSpec("custom", "Custom", base, bottom, bar, accent, dark))
    }

    /** His rule: dark base gets a lighter bar, light base a darker bar, and a
     * neutral base can never get a same-colour bar - it defaults darker. */
    fun suggestBar(base: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        val s = hsl[1]
        val l = hsl[2]
        val out = FloatArray(3)
        out[0] = hsl[0]
        when {
            s < 0.12f -> {
                out[1] = 0f
                out[2] = (l - 0.35f).coerceAtLeast(0.14f)
            }
            l < 0.45f -> {
                out[1] = (s * 0.9f).coerceIn(0f, 1f)
                out[2] = (l + 0.32f).coerceAtMost(0.85f)
            }
            else -> {
                out[1] = (s * 1.05f).coerceIn(0f, 1f)
                out[2] = (l - 0.30f).coerceAtLeast(0.16f)
            }
        }
        return Color(ColorUtils.HSLToColor(out))
    }

    fun shadeOf(c: Color, dl: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c.toArgb(), hsl)
        hsl[2] = (hsl[2] + dl).coerceIn(0.08f, 0.92f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/** Little four-dot palette, drawn so no icon dependency is needed. */
@Composable
fun PaletteIcon() {
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2f
        val dot = r * 0.42f
        val ox = r * 0.42f
        val oy = r * 0.42f
        drawCircle(Color(0xFF4E8DF5), dot, center.copy(x = center.x - ox, y = center.y - oy))
        drawCircle(Color(0xFF2EBD85), dot, center.copy(x = center.x + ox, y = center.y - oy))
        drawCircle(Color(0xFFF2A93B), dot, center.copy(x = center.x - ox, y = center.y + oy))
        drawCircle(Color(0xFF9B6BF3), dot, center.copy(x = center.x + ox, y = center.y + oy))
    }
}

@Composable
fun ThemeSection() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap a theme to apply it. Saved on this device.", style = MaterialTheme.typography.bodySmall)
        ThemeStore.presets.forEach { t ->
            val selected = ThemeStore.current.id == t.id
            Card(
                Modifier.fillMaxWidth().clickable { ThemeStore.apply(context, t) },
                colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else CardDefaults.cardColors()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(t.bgTop).border(1.dp, Color(0x33000000), CircleShape))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(t.bar).border(1.dp, Color(0x33000000), CircleShape))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(t.accent).border(1.dp, Color(0x33000000), CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t.name, Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    if (selected) Text("in use", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Make your own", style = MaterialTheme.typography.titleMedium)
        CustomThemeMaker()
    }
}

@Composable
private fun CustomThemeMaker() {
    val context = LocalContext.current
    var hue by remember { mutableFloatStateOf(210f) }
    var sat by remember { mutableFloatStateOf(0.45f) }
    var lig by remember { mutableFloatStateOf(0.72f) }
    val base = Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, lig)))
    val suggested = ThemeStore.suggestBar(base)
    var barChoice by remember { mutableStateOf<Color?>(null) }
    // A new base colour re-suggests the bar; manual picks don't stick.
    LaunchedEffect(base) { barChoice = null }
    val bar = barChoice ?: suggested
    val dark = lig < 0.45f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Base colour")
        Slider(hue, { hue = it }, valueRange = 0f..360f)
        Text("Colour strength" + if (sat < 0.12f) " (neutral: the bar defaults darker)" else "")
        Slider(sat, { sat = it }, valueRange = 0f..1f)
        Text(if (dark) "Dark base - we suggest a lighter bar" else "Light base - we suggest a darker bar")
        Slider(lig, { lig = it }, valueRange = 0.15f..0.9f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(base).border(1.dp, Color(0x33000000), CircleShape))
                Text("base", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(bar).border(1.dp, Color(0x33000000), CircleShape))
                Text("bar", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val altDark = ThemeStore.shadeOf(bar, -0.18f)
                val altLight = ThemeStore.shadeOf(bar, 0.18f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(altDark).border(1.dp, Color(0x33000000), CircleShape).clickable { barChoice = altDark })
                    Box(Modifier.size(30.dp).clip(CircleShape).background(altLight).border(1.dp, Color(0x33000000), CircleShape).clickable { barChoice = altLight })
                }
                Text("other bar picks", style = MaterialTheme.typography.labelSmall)
            }
        }
        Button(onClick = { ThemeStore.applyCustom(context, base, bar, dark) }) { Text("Use this theme") }
    }
}
