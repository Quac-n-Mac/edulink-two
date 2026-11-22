package dev.edulinktwo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EdulinkBlue = Color(0xFF00365D)
val EdulinkBlueLight = Color(0xFF0A4E85)
val EdulinkAccent = Color(0xFF4CA1E3)

private val scheme = lightColorScheme(
    primary = EdulinkBlue,
    secondary = EdulinkBlueLight,
    tertiary = EdulinkAccent,
    surface = Color(0xFFF6F8FA),
    background = Color(0xFFF6F8FA)
)

/** All transitions run at ~40% of stock duration (120ms) - snappy, no slow fades. */
const val FAST_MS = 120

@Composable
fun EdulinkTwoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
