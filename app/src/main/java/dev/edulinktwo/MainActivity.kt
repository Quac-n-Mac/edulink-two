package dev.edulinktwo

import android.os.Bundle
import android.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import dev.edulinktwo.ui.TapFeedbackHost
import dev.edulinktwo.ui.VisualTuning
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.edulinktwo.ui.EdulinkTwoTheme
import dev.edulinktwo.ui.HomeScreen
import dev.edulinktwo.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VisualTuning.load(this)
        window.statusBarColor = Color.rgb(246, 247, 252)
        window.navigationBarColor = Color.rgb(246, 247, 252)
        window.decorView.setBackgroundColor(Color.rgb(246, 247, 252))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            EdulinkTwoTheme {
                val vm: AppViewModel = viewModel()
                val ui by vm.ui.collectAsState()
                TapFeedbackHost {
                if (ui.loggedIn && !ui.showLogin) {
                    HomeScreen(ui = ui, vm = vm)
                } else {
                    LoginScreen(ui = ui, vm = vm)
                }
                }
            }
        }
    }
}
