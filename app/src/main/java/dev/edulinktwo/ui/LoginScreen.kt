package dev.edulinktwo.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.edulinktwo.AppViewModel
import dev.edulinktwo.R
import dev.edulinktwo.SchoolResolveResult
import dev.edulinktwo.UiState

// Stock Edulink One login palette, lifted from the original stylesheets.
private val GradTop = Color(0xFF7C8CCA)
private val GradBottom = Color(0xFF4D9DCD)
private val CardBlue = Color(0x99334A7A) // rgba(51,74,122,.6)
private val LabelWhite = Color(0xB3FFFFFF) // hsla(0,0%,100%,.7)
private val CoralTop = Color(0xFFFF9168)
private val CoralBottom = Color(0xFFFD746C)
private val GreyTop = Color(0xFFBFC6CE)
private val GreyBottom = Color(0xFF8C97A5)

private enum class LoginStep { PICKER, CODE, CREDENTIALS }

@Composable
fun LoginScreen(ui: UiState, vm: AppViewModel) {
    var step by remember {
        // Relaunch with saved accounts -> the picker (stock parity).
        // First run, or "add account" from inside the app -> school code.
        mutableStateOf(if (ui.accounts.isNotEmpty() && !ui.showLogin && ui.currentAccount == null) LoginStep.PICKER else LoginStep.CODE)
    }
    var school by remember { mutableStateOf<SchoolResolveResult?>(null) }
    var schoolCode by remember { mutableStateOf("") }

    val tilt by rememberPerspective()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1:1 background: gradient + the original doodle overlay.
        val parallax = Modifier.fillMaxSize().graphicsLayer {
            // deep layer: drifts WITH rotation, oversized to hide edges
            scaleX = 1.3f; scaleY = 1.3f
            rotationZ = tilt.x * 2.5f
            translationX = tilt.x * 60.dp.toPx()
            translationY = tilt.y * 44.dp.toPx()
        }
        Box(
            modifier = parallax
                .background(Brush.verticalGradient(listOf(GradTop, GradBottom)))
        )
        Image(
            painter = painterResource(R.drawable.login_bg),
            contentDescription = null,
            modifier = parallax,
            contentScale = ContentScale.Crop
        )

        // Foreground counter-shift: cards/buttons move against the
        // background so the depth separation reads on slight rotation.
        val fg = Modifier.graphicsLayer {
            translationX = -tilt.x * 40.dp.toPx()
            translationY = -tilt.y * 28.dp.toPx()
        }
        Box(Modifier.fillMaxSize().then(fg)) {
        when (step) {
            LoginStep.PICKER -> AccountPicker(
                ui = ui, vm = vm,
                onAdd = { step = LoginStep.CODE },
                onPicked = { /* switchAccount navigates via loggedIn */ }
            )
            LoginStep.CODE -> SchoolCodeStep(
                hasAccounts = ui.accounts.isNotEmpty(),
                onBack = { step = LoginStep.PICKER },
                onResolved = { code, result ->
                    schoolCode = code
                    school = result
                    step = LoginStep.CREDENTIALS
                },
                vm = vm
            )
            LoginStep.CREDENTIALS -> CredentialsStep(
                school = school!!,
                schoolCode = schoolCode,
                onBack = { step = LoginStep.CODE },
                vm = vm
            )
        }
        }

        // Language chip, bottom-right (visual parity; English only for now).
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("English", color = Color(0xFF333333), fontSize = 13.sp)
            Text("  \u25BE", color = Color(0xFF333333), fontSize = 11.sp)
        }
    }
}

@Composable
private fun BackCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("\u2039", color = Color(0xFF555555), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val brush = if (enabled) Brush.verticalGradient(listOf(CoralTop, CoralBottom))
    else Brush.verticalGradient(listOf(GreyTop, GreyBottom))
    Box(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .height(47.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(brush)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (busy) "..." else text,
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DemoButton(onClick: () -> Unit, backdrop: GlassBackdrop? = null, lightDir: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset(-0.55f, -0.83f) }) {
    val glassMod = if (backdrop != null) {
        Modifier.liquidGlass(backdrop, cornerRadius = 30.dp, edgeWidth = 10.dp, refrStrength = 9.dp, baseAlpha = 0.4f, lightDir = lightDir)
    } else {
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .border(1.5.dp, Color.White, RoundedCornerShape(30.dp))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .height(47.dp)
            .then(glassMod)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("DEMO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = LabelWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun loginFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
    focusedIndicatorColor = Color.White,
    unfocusedIndicatorColor = LabelWhite,
    focusedLabelColor = LabelWhite,
    unfocusedLabelColor = LabelWhite
)

@Composable
private fun SchoolCodeStep(
    hasAccounts: Boolean,
    backdrop: GlassBackdrop? = null,
    lightDir: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset(-0.55f, -0.83f) },
    onBack: () -> Unit,
    onResolved: (String, SchoolResolveResult) -> Unit,
    vm: AppViewModel
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasAccounts) {
            Box(modifier = Modifier.align(Alignment.TopStart)) { BackCircle(onBack) }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.two_logo),
                contentDescription = "Edulink Two",
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(CardBlue)
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                FieldLabel("SCHOOL ID OR POSTCODE")
                TextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = loginFieldColors()
                )
                if (error != null) {
                    Text(error!!, color = Color(0xFFFFC9C4), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = "NEXT",
                enabled = code.isNotBlank(),
                busy = busy,
                onClick = {
                    busy = true
                    vm.resolveSchool(code) { ok, result, err ->
                        busy = false
                        if (ok && result != null) onResolved(code.trim(), result) else error = err ?: "school not found"
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            DemoButton(onClick = { vm.loginDemo() })
        }
    }
}

@Composable
private fun CredentialsStep(
    school: SchoolResolveResult,
    schoolCode: String,
    backdrop: GlassBackdrop? = null,
    lightDir: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset(-0.55f, -0.83f) },
    onBack: () -> Unit,
    vm: AppViewModel
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val crest = remember(school.school_id) {
        vm.crestFile(school.school_id)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart)) { BackCircle(onBack) }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Crest circle + school name, like the stock app.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (crest != null) {
                    Image(bitmap = crest, contentDescription = school.name, modifier = Modifier.size(72.dp).clip(CircleShape))
                } else {
                    Text(school.name.take(1), color = EdulinkBlue, fontSize = 34.sp, fontFamily = FontFamily.Serif)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                school.name,
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(CardBlue)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                FieldLabel("USERNAME")
                TextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = loginFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel("PASSWORD")
                TextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = loginFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(1.dp, LabelWhite, RoundedCornerShape(2.dp))
                            .clickable { rememberMe = !rememberMe },
                        contentAlignment = Alignment.Center
                    ) {
                        if (rememberMe) Text("\u2713", color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("REMEMBER ME?", color = LabelWhite, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "RESET PASSWORD",
                        color = LabelWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://${school.host}/")
                            )
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { vm.getApplication<android.app.Application>().startActivity(intent) }
                        }
                    )
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFFFC9C4), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = "LOG IN",
                enabled = username.isNotBlank() && password.isNotBlank(),
                busy = busy,
                onClick = {
                    busy = true
                    error = null
                    vm.login(school.host, schoolCode, school.school_id, username, password, "") { ok, err ->
                        busy = false
                        if (!ok) error = err
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            DemoButton(onClick = { vm.loginDemo() })
        }
    }
}

@Composable
private fun AccountPicker(
    ui: UiState,
    vm: AppViewModel,
    onAdd: () -> Unit,
    onPicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ui.accounts.forEach { account ->
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable {
                            vm.switchAccount(account.key)
                            onPicked()
                        }
                        .padding(start = 56.dp, top = 18.dp, bottom = 18.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            account.school,
                            color = Color(0xFF555555),
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            account.display_name,
                            color = Color(0xFF999999),
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "\u2715",
                        color = Color(0xFF333333),
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { vm.removeAccount(account.key) }
                            .padding(10.dp)
                    )
                }
                // Crest circle overlapping the card's left edge.
                val crest = remember(account.school_id) {
                    vm.crestFile(account.school_id)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-14).dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0x11000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (crest != null) {
                        Image(bitmap = crest, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                    } else {
                        Text(
                            account.school.take(1),
                            color = EdulinkBlue,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        // Big outlined + button.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        }
    }
}
