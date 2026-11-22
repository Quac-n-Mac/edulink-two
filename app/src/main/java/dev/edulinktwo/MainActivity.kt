package dev.edulinktwo

import android.os.Bundle
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
        setContent {
            EdulinkTwoTheme {
                val vm: AppViewModel = viewModel()
                val ui by vm.ui.collectAsState()
                if (ui.loggedIn && !ui.showLogin) {
                    HomeScreen(ui = ui, vm = vm)
                } else {
                    LoginScreen(ui = ui, vm = vm)
                }
            }
        }
    }
}
