package dev.edulinktwo.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

val EdulinkBlue = Color(0xFF00365D)
val EdulinkBlueLight = Color(0xFF0A4E85)
val EdulinkAccent = Color(0xFF4CA1E3)

/** All transitions run at ~40% of stock duration (120ms) - snappy, no slow fades. */
const val FAST_MS = 120

@Composable
fun EdulinkTwoTheme(content: @Composable () -> Unit) {
    val t = ThemeStore.current
    val scheme = remember(t) {
        if (t.dark) darkColorScheme(primary = t.accent, background = t.bgTop, surface = t.bgTop)
        else lightColorScheme(primary = t.accent, secondary = EdulinkBlueLight, tertiary = EdulinkAccent,
            surface = t.bgTop, background = t.bgTop)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !t.dark
                isAppearanceLightNavigationBars = !t.dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
