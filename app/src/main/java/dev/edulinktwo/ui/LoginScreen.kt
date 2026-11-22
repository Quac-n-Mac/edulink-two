package dev.edulinktwo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.edulinktwo.AppViewModel
import dev.edulinktwo.R
import dev.edulinktwo.UiState

@Composable
fun LoginScreen(ui: UiState, vm: AppViewModel) {
    var host by remember { mutableStateOf("www9.edulinkone.com") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var learnerId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.header_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Text("Edulink Two", style = MaterialTheme.typography.headlineLarge, color = androidx.compose.ui.graphics.Color.White)
        }
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(host, { host = it }, label = { Text("Server (host)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(learnerId, { learnerId = it }, label = { Text("Learner id (usually optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    busy = true
                    error = null
                    vm.login(host, username, password, learnerId) { ok, err ->
                        busy = false
                        if (!ok) error = err
                    }
                },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Signing in..." else "Sign in")
            }
        }
    }
}
