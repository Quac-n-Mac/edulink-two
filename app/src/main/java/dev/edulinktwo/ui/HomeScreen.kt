package dev.edulinktwo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import dev.edulinktwo.*
import dev.edulinktwo.R
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(ui: UiState, vm: AppViewModel) {
    var accountSheet by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<HomeworkDetail?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var attachmentBusy by remember { mutableStateOf(false) }
    var lessonSel by remember { mutableStateOf<Lesson?>(null) }

    // Back gesture: sheets dismiss first, then tabs pop to root; the system
    // only exits the app at the root tab.
    val anySheet = detail != null || detailLoading || detailError != null
    BackHandler(enabled = anySheet) { detail = null; detailLoading = false; detailError = null }
    BackHandler(enabled = accountSheet) { accountSheet = false }
    BackHandler(enabled = lessonSel != null) { lessonSel = null }
    BackHandler(enabled = !anySheet && !accountSheet && lessonSel == null && ui.tab != Tab.HOMEWORK) { vm.selectTab(Tab.HOMEWORK) }

    // 3-layer parallax: a deep background plane drifts against scroll and
    // device tilt, content scrolls at full speed, and the bars stay pinned
    // above it with a shadow that appears once content is under them.
    val tilt by rememberPerspective()
    var scrollPx by remember { mutableFloatStateOf(0f) }
    val scrollProbe = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                scrollPx = (scrollPx + consumed.y).coerceIn(0f, 12000f)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(ui.tab) { scrollPx = 0f }
    val barElev = if (scrollPx > 6f) 7.dp else 3.dp
    val softAmbient = Color.Black.copy(alpha = 0.08f)
    val softSpot = Color.Black.copy(alpha = 0.10f)

    // His revised layer feel (v0.5.7): the bars sit nearly STATIC so
    // they never get hard to read, the page content carries the
    // parallax, and detail sheets counter-shift (negative parallax)
    // because they float above the bars. Background drifts slowest.
    // Parallax at 50% per his v0.5.7 verdict. The bg scroll drift is
    // clamped hard: an unbounded shift slid the light background away
    // on long scrolls and exposed the navy root (his "growing blue bar").
    val drift = Modifier.graphicsLayer {
        scaleX = 1.1f; scaleY = 1.1f
        rotationZ = (tilt.x * 0.4f) * VisualTuning.motionStrength
        translationY = (-scrollPx.coerceIn(-220f, 220f) * 0.18f + tilt.y * 4.dp.toPx()) * VisualTuning.motionStrength
        translationX = (tilt.x * 6.dp.toPx()) * VisualTuning.motionStrength
    }
    val contentDrift = Modifier.graphicsLayer {
        translationY = (-tilt.y * 6.dp.toPx()) * VisualTuning.motionStrength
        translationX = (-tilt.x * 10.dp.toPx()) * VisualTuning.motionStrength
    }
    // Sheets no longer drift: they are glass panels sampling the static
    // backdrop beneath them, and a translated lens over an untranslated
    // sample reads as a sampling bug. (This supersedes his earlier
    // negative-parallax-on-sheets ask - the glass IS the depth now.)
    val nearDrift = Modifier.graphicsLayer {
        translationY = (-tilt.y * 1.5f.dp.toPx()) * VisualTuning.motionStrength
        translationX = (-tilt.x * 2.dp.toPx()) * VisualTuning.motionStrength
    }

    // Liquid glass backdrop: the WHOLE screen (bars included) is
    // recorded into a texture, so the glass refracts whatever is truly
    // on screen under the circle - not a curated layer.
    val glassBackdrop = remember { GlassBackdrop() }
    val glassLayer = rememberGraphicsLayer()
    val glassScope = rememberCoroutineScope()

    // Nav slide state lives at screen scope so the glass pill can float
    // ABOVE the captured screen as an overlay (never refracting itself).
    val navTabs = listOf(Tab.HOMEWORK, Tab.TIMETABLE, Tab.PROFILE, Tab.POINTS, Tab.MORE)
    val navStains = listOf(
        Color(0xFF4E8DF5), // Homework - blue
        Color(0xFF2EBD85), // Timetable - green
        Color(0xFF9B6BF3), // Profile - purple (goes clear on land)
        Color(0xFFF2A93B), // Points - amber
        Color(0xFF7A8AA0)  // More - slate
    )
    val selIdx = navTabs.indexOf(ui.tab).coerceAtLeast(0)
    var dragIndex by remember { mutableStateOf<Float?>(null) }
    val slideIdx by animateFloatAsState(
        targetValue = dragIndex ?: selIdx.toFloat(),
        animationSpec = if (dragIndex != null) snap() else tween(if (VisualTuning.systemReducedMotion) 0 else VisualTuning.animationMs.toInt(), easing = EaseInOut),
        label = "navSlide")
    val lo = kotlin.math.floor(slideIdx).toInt().coerceIn(0, navTabs.lastIndex)
    val hi = kotlin.math.ceil(slideIdx).toInt().coerceIn(0, navTabs.lastIndex)
    val baseStain = lerp(navStains[lo], navStains[hi], slideIdx - lo)
    val profFactor = (1f - kotlin.math.abs(slideIdx - 2f)).coerceIn(0f, 1f)
    val stainAlpha = 0.55f * (1f - profFactor)
    val pillH = 62.dp + 14.dp * profFactor
    val pillPad = 8.dp * (1f - profFactor) + 1.5.dp * profFactor
    val pillDrift = Modifier.graphicsLayer {
        translationY = (-tilt.y * 2.dp.toPx()) * VisualTuning.motionStrength
        translationX = (-tilt.x * 3.dp.toPx()) * VisualTuning.motionStrength
    }
    var navPos by remember { mutableStateOf(Offset.Zero) }
    var navSize by remember { mutableStateOf(IntSize(1, 1)) }
    var avatarCenterY by remember { mutableStateOf<Float?>(null) }

    // Recapture triggers: page change, data refresh, every slide
    // animation frame, and whenever scrolling settles.
    LaunchedEffect(ui.tab) { glassBackdrop.captureVersion++ }
    LaunchedEffect(ui.lastUpdated) { glassBackdrop.captureVersion++ }
    LaunchedEffect(Unit) {
        snapshotFlow { slideIdx }.collect { glassBackdrop.captureVersion++ }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { scrollPx }.collectLatest {
            delay(160)
            glassBackdrop.captureVersion++
        }
    }

    Box(Modifier.fillMaxSize()) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bgBlur = if (android.os.Build.VERSION.SDK_INT >= 31 && (anySheet || accountSheet || lessonSel != null))
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(VisualTuning.sheetBlur.coerceAtLeast(0.1f), VisualTuning.sheetBlur.coerceAtLeast(0.1f), android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else Modifier
    Column(Modifier.fillMaxSize().then(bgBlur).nestedScroll(scrollProbe).glassBackdrop(glassBackdrop, glassLayer, glassScope, 0.5f)) {
        Box(Modifier.fillMaxSize().padding(top = 64.dp + statusTop, bottom = 88.dp + navBottom)) {
        // Layer 1 (deep): barely-there gradient + faint doodle texture.
        Box(
            Modifier
                .fillMaxSize()
                .then(drift)
                .background(Brush.verticalGradient(listOf(Color(0xFFF6F8FA), Color(0xFFE1E8F0))))
        )
        Image(
            painter = painterResource(R.drawable.login_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.07f,
            modifier = Modifier.fillMaxSize().then(drift)
        )

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().then(contentDrift)
        ) {
            // Fast transitions: 120ms, no slow fades.
            AnimatedContent(
                targetState = ui.tab,
                transitionSpec = { fadeIn(tween(FAST_MS)) togetherWith fadeOut(tween(FAST_MS)) },
                label = "tab"
            ) { tab ->
                when (tab) {
                    Tab.HOMEWORK -> HomeworkTab(ui, vm) { id, src ->
                        detailError = null
                        detailLoading = true
                        detail = null
                        vm.homeworkDetail(id, src) { d, err ->
                            detailLoading = false
                            if (d != null) detail = d else detailError = err ?: "could not load this homework"
                        }
                    }
                    Tab.TIMETABLE -> TimetableTab(ui) { lessonSel = it }
                    Tab.POINTS -> PointsTab(ui)
                    Tab.MORE -> MoreTab(ui, vm)
                    Tab.PROFILE -> ProfileTab(ui, vm)
                }
            }
        }
        }
    }
    // Frosted white bottom bar floating over the content: strong blur,
    // a whisper of refraction, soft letter edges bleed through. No clip,
    // so the raised profile avatar escapes the bar.
    Box(Modifier.align(Alignment.BottomCenter).then(nearDrift)
            .shadow(barElev, clip = false, ambientColor = softAmbient, spotColor = softSpot)
            .liquidGlass(glassBackdrop, cornerRadius = 0.dp, edgeWidth = 4.dp, refrStrength = 3.dp, blurRadius = 14.dp, baseAlpha = 0.55f, stainColor = Color.White, stain = 0.5f)
            .navigationBarsPadding()
            .onGloballyPositioned { navPos = it.positionInRoot(); navSize = it.size }) {
            Row(Modifier.fillMaxWidth().height(80.dp).pointerInput(navSize.width) {
                val itemPx = navSize.width.toFloat() / navTabs.size
                detectHorizontalDragGestures(
                    onDragStart = { at -> dragIndex = (at.x / itemPx - 0.5f).coerceIn(0f, navTabs.lastIndex.toFloat()) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragIndex = ((dragIndex ?: selIdx.toFloat()) + amount / itemPx).coerceIn(0f, navTabs.lastIndex.toFloat())
                    },
                    onDragEnd = {
                        dragIndex?.let { vm.selectTab(navTabs[it.roundToInt().coerceIn(0, navTabs.lastIndex)]) }
                        dragIndex = null
                    },
                    onDragCancel = { dragIndex = null }
                )
            }) {
                GlassNavItem(ui.tab == Tab.HOMEWORK, { vm.selectTab(Tab.HOMEWORK) }, { Icon(Icons.Default.Home, null) }, "Homework", Modifier.weight(1f))
                GlassNavItem(ui.tab == Tab.TIMETABLE, { vm.selectTab(Tab.TIMETABLE) }, { Icon(Icons.Default.DateRange, null) }, "Timetable", Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ProfileNavButton(ui, vm, onTap = { vm.selectTab(Tab.PROFILE) }, onLong = { accountSheet = true }, onPlaced = { avatarCenterY = it })
                }
                GlassNavItem(ui.tab == Tab.POINTS, { vm.selectTab(Tab.POINTS) }, { Icon(Icons.Default.Star, null) }, "Points", Modifier.weight(1f))
                GlassNavItem(ui.tab == Tab.MORE, { vm.selectTab(Tab.MORE) }, { Icon(Icons.Default.Menu, null) }, "More", Modifier.weight(1f))
            }
    }

    // The one shared stained-glass pill: slides to the tapped tab,
    // lerping its stain through every page colour it passes; over
    // PROFILE it expands into a clear ring around the avatar. It sits
    // ABOVE the captured screen and refracts the real pixels beneath
    // it; its shadow is generated from its own live stain colour.
    val density = LocalDensity.current
    if (navSize.width > 1) {
        val itemW = with(density) { (navSize.width / navTabs.size).toDp() }
        val navY = with(density) { navPos.y.toDp() }
        val itemCenterY = navY + 40.dp // items live in the fixed 80dp Row; navSize.height includes the gesture inset
        val profCenterY = avatarCenterY?.let { with(density) { it.toDp() } } ?: (itemCenterY - 16.dp)
        val centerY = itemCenterY + (profCenterY - itemCenterY) * profFactor
        val profileMinWidth = 72.dp * profFactor
        val lensWidth = maxOf(itemW - pillPad * 2, profileMinWidth)
        Box(
            Modifier
                .offset(x = itemW * (slideIdx + 0.5f) - lensWidth / 2, y = centerY - pillH / 2)
                .width(lensWidth)
                .height(pillH)
                .shadow(10.dp, RoundedCornerShape(pillH / 2), clip = false, ambientColor = baseStain, spotColor = baseStain)
                .then(pillDrift)
                .liquidGlass(glassBackdrop, cornerRadius = pillH / 2, edgeWidth = 7.dp, refrStrength = 7.dp, baseAlpha = 0.42f, stainColor = baseStain, stain = stainAlpha)
        )
    }

    // Top bar as a liquid-glass panel floating over the content: it
    // refracts the parallax background drifting beneath it.
    TopAppBar(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .then(nearDrift)
            .shadow(barElev, clip = false, ambientColor = softAmbient, spotColor = softSpot)
            .liquidGlass(glassBackdrop, cornerRadius = 0.dp, edgeWidth = 5.dp, refrStrength = 5.dp, blurRadius = 4.dp, baseAlpha = 0.65f)
            .statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Column {
                Text("Edulink Two")
                Text(
                    "updated ${ageLabel(ui.lastUpdated)}" +
                        (if (ui.offline) " - offline, last-known data" else if (ui.degraded) " - slow connection mode" else ""),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        actions = {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
        }
    )
    }

    if (detail != null || detailLoading || detailError != null) {
        ModalBottomSheet(
            onDismissRequest = { detail = null; detailLoading = false; detailError = null },
            containerColor = Color.Transparent
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).liquidGlass(glassBackdrop, cornerRadius = 28.dp, edgeWidth = 8.dp, refrStrength = 8.dp, blurRadius = 8.dp, baseAlpha = 0.82f).padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                when {
                    detailLoading -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
                    detail == null -> Text(detailError ?: "could not load homework", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        val d = detail!!
                        Text(d.homework.activity, style = MaterialTheme.typography.titleLarge)
                        Text("${d.homework.subject} - due ${d.homework.due_date}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(stripHtml(d.homework.description), style = MaterialTheme.typography.bodyMedium)
                        if (d.homework.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Attachments", style = MaterialTheme.typography.titleSmall)
                            d.homework.attachments.forEach { att ->
                                ListItem(
                                    headlineContent = { Text(att.filename ?: "attachment") },
                                    leadingContent = { Icon(Icons.Default.Send, null) },
                                    modifier = Modifier.clickable(enabled = !attachmentBusy) {
                                        attachmentBusy = true
                                        vm.openAttachment(att.idString()) { ok, payload, err ->
                                            attachmentBusy = false
                                            if (ok && payload != null) {
                                                val uri = android.net.Uri.parse(payload.substringBefore("|"))
                                                val mime = payload.substringAfter("|", "*/*")
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, mime)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching { vm.getApplication<android.app.Application>().startActivity(intent) }
                                            } else {
                                                detailError = err
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        detailError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    lessonSel?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { lessonSel = null }, containerColor = Color.Transparent) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).liquidGlass(glassBackdrop, cornerRadius = 28.dp, edgeWidth = 8.dp, refrStrength = 8.dp, blurRadius = 8.dp, baseAlpha = 0.82f)) { LessonSheet(ui, vm, lesson) }
        }
    }

    if (accountSheet) {
        ModalBottomSheet(onDismissRequest = { accountSheet = false }, containerColor = Color.Transparent) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).liquidGlass(glassBackdrop, cornerRadius = 28.dp, edgeWidth = 8.dp, refrStrength = 8.dp, blurRadius = 8.dp, baseAlpha = 0.82f).padding(bottom = 32.dp)) {
                Text("Profiles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                ui.accounts.forEach { a ->
                    ListItem(
                        headlineContent = { Text(a.display_name + if (a.key == ui.currentAccount) "  ✓" else "") },
                        supportingContent = { Text("${a.username} - ${a.school}") },
                        trailingContent = {
                            IconButton(onClick = { vm.removeAccount(a.key); accountSheet = false }) {
                                Icon(Icons.Default.Delete, "Remove")
                            }
                        },
                        modifier = Modifier.clickable {
                            accountSheet = false
                            if (a.key != ui.currentAccount) vm.switchAccount(a.key)
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Add profile") },
                    leadingContent = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.clickable { accountSheet = false; vm.showAddAccount() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileNavButton(ui: UiState, vm: AppViewModel, onTap: () -> Unit, onLong: () -> Unit, onPlaced: (Float) -> Unit = {}) {
    val avatar = remember(ui.currentAccount) {
        vm.avatarFile(ui.currentAccount)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .offset(y = (-16).dp)
            .onGloballyPositioned { onPlaced(it.positionInRoot().y + it.size.height / 2f) }
            .size(66.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White, CircleShape)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Image(bitmap = avatar, contentDescription = "Profile", modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Icon(Icons.Default.Person, "Profile", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

@Composable
fun HomeworkTab(ui: UiState, vm: AppViewModel, onTap: (String, String) -> Unit) {
    val hw = ui.homework
    if (hw == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No homework cached yet. Pull to refresh.") }
        return
    }
    var showPast by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Current", style = MaterialTheme.typography.titleMedium) }
        items(hw.homework.current, key = { "cur-" + it.idString() + it.due_date }) { h ->
            val due = runCatching { LocalDate.parse(h.due_date.take(10)) }.getOrNull()
            val days = due?.toEpochDay()?.minus(today.toEpochDay())?.toInt()
            val rowColor = when {
                h.completed -> Color(0xFFC8E6C9) // done: green
                days != null && days <= 0 && days > -730 -> Color(0xFFFFCDD2) // due today/overdue: red
                days == 1 -> Color(0xFFFFE0B2) // due tomorrow: orange
                else -> Color(0xFFECEFF1) // 2+ days: grey
            }
            val dueLabel = when {
                days == null -> "due ${h.due_date}"
                kotlin.math.abs(days) > 730 -> "due ${h.due_date.take(10)}" // keep extreme dates readable
                days < 0 -> "${-days}d overdue"
                days == 0 -> "due today"
                days == 1 -> "due tomorrow"
                else -> "in ${days}d"
            }
            Card(
                Modifier.fillMaxWidth().clickable {
                    onTap(h.idString(), h.source ?: "EduLink")
                },
                colors = CardDefaults.cardColors(containerColor = rowColor)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(h.activity, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(h.subject, style = MaterialTheme.typography.bodySmall)
                        Text(dueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    if (h.completed) Text("Done", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (hw.homework.past.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { showPast = !showPast }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Past (${hw.homework.past.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (showPast) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showPast) "Hide past" else "Show past"
                    )
                }
            }
            if (showPast) {
                items(hw.homework.past, key = { "past-" + it.idString() + it.due_date }) { h ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(h.activity)
                            Text("${h.subject} - was due ${h.due_date}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableTab(ui: UiState, onLessonTap: (Lesson) -> Unit = {}) {
    val tb = ui.timetable
    if (tb == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No timetable cached yet. Pull to refresh.") }
        return
    }
    val today = LocalDate.now().toString()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tb.weeks.forEach { week ->
            week.days.forEach { day ->
                item {
                    Text(
                        "${day.name ?: ""} (${day.date})" + if (day.date == today) " - today" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                items(day.lessons.size, key = { day.date + "-lesson-" + it }) { idx -> val l = day.lessons[idx]
                    Card(Modifier.fillMaxWidth().clickable { onLessonTap(l) }) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${l.start_time ?: ""}\n${l.end_time ?: ""}", style = MaterialTheme.typography.labelSmall)
                            Column {
                                Text(l.subjectName(), fontWeight = FontWeight.SemiBold)
                                Text("${l.room?.name ?: ""}  ${l.teacherName()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PointsTab(ui: UiState) {
    val a = ui.achievement
    val b = ui.behaviour
    if (a == null || b == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("No points cached yet. Pull to refresh.")
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        return
    }
    val aPts = a.achievement.sumOf { it.points }
    val bPts = b.behaviour.sumOf { it.points }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+$aPts", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Achievement", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("-$bPts", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                        Text("Behaviour", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${aPts - bPts}", style = MaterialTheme.typography.headlineMedium)
                        Text("Net", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { Text("Achievements", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(a.achievement, key = { i, e -> "ach-$i" }) { _, e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("+${e.points}  ${e.comments ?: ""}")
                    Text(e.date, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Behaviour", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(b.behaviour, key = { i, e -> "beh-$i" }) { _, e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("-${e.points}  ${e.comments ?: ""}")
                    Text("${e.date}  ${e.lesson_information ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (b.detentions.isNotEmpty()) {
            item { Text("Detentions", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(b.detentions, key = { i, _ -> "det-$i" }) { _, d ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.description ?: "")
                        Text("${d.date ?: ""}  ${d.location ?: ""}  ${d.attended ?: ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


@Composable
fun MoreTab(ui: UiState, vm: AppViewModel) {
    var section by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    val listFade = remember { Animatable(1f) }
    LaunchedEffect(pending) {
        if (pending != null) {
            listFade.snapTo(1f)
            listFade.animateTo(0f, tween(if (VisualTuning.systemReducedMotion) 0 else 200))
            section = pending
            pending = null
            listFade.snapTo(1f)
        }
    }
    BackHandler(enabled = section != null) { section = null }
    when (section) {
        null -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val items = mutableListOf("Attendance", "Notices", "Exams", "Calendar", "Clubs")
                if (ui.inbox != null || ui.outbox != null) items += "Messages"
                if (ui.registers != null) items += "Registers"
                if (ui.seatingPlans != null) items += "Seating Plans"
                if (ui.catering != null) items += "Catering"
                if (ui.documents != null) items += "Documents"
                if (ui.externalLinks != null) items += "Links"
                if (ui.medical != null) items += "Medical"
                if (ui.teachers != null) items += "Teachers"
                items += "Customisation"
                items.forEach { name ->
                    item {
                        Card(Modifier.fillMaxWidth()
                            .graphicsLayer { alpha = if (pending == null || name == pending) 1f else listFade.value }
                            .clickable(enabled = pending == null) { pending = name }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (name == "Customisation") {
                                    PaintbrushIcon()
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        "Customisation" -> MoreSection(title = "Customisation", onBack = { section = null }) { CustomisationSection() }
        "Attendance" -> MoreSection(title = "Attendance", onBack = { section = null }) { AttendanceSection(ui) }
        "Notices" -> MoreSection(title = "Notices", onBack = { section = null }) { NoticesSection(ui, vm) }
        "Exams" -> MoreSection(title = "Exams", onBack = { section = null }) { ExamsSection(ui) }
        "Calendar" -> MoreSection(title = "Calendar", onBack = { section = null }) { CalendarSection(ui) }
        "Clubs" -> MoreSection(title = "Clubs", onBack = { section = null }) { ClubsSection(ui) }
        "Messages" -> MoreSection(title = "Messages", onBack = { section = null }) { MessagesSection(ui) }
        "Registers" -> MoreSection(title = "Registers", onBack = { section = null }) { RegistersSection(ui, vm) }
        "Seating Plans" -> MoreSection(title = "Seating Plans", onBack = { section = null }) { SeatingPlansSection(ui, vm) }
        "Catering" -> MoreSection(title = "Catering", onBack = { section = null }) { CateringSection(ui) }
        "Documents" -> MoreSection(title = "Documents", onBack = { section = null }) { DocumentsSection(ui) }
        "Links" -> MoreSection(title = "Links", onBack = { section = null }) { LinksSection(ui) }
        "Medical" -> MoreSection(title = "Medical", onBack = { section = null }) { MedicalSection(ui) }
        "Teachers" -> MoreSection(title = "Teachers", onBack = { section = null }) { TeachersSection(ui) }
    }
}

@Composable
private fun MoreSection(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val progress = remember(title) { Animatable(0f) }
    LaunchedEffect(title) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (VisualTuning.systemReducedMotion) 0 else 1000, easing = EaseInOut))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< More") }
            Row {
                title.forEachIndexed { i, c ->
                    val start = 0.05f + i * 0.035f
                    val lp = ((progress.value - start) / 0.45f).coerceIn(0f, 1f)
                    val ease = 1f - (1f - lp) * (1f - lp)
                    Text(c.toString(), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.graphicsLayer {
                            alpha = lp
                            translationY = (1f - ease) * 26.dp.toPx()
                            val sc = 1.25f - 0.25f * ease
                            scaleX = sc; scaleY = sc
                            rotationZ = ((i % 3) - 1) * 5f * (1f - ease)
                        })
                }
            }
        }
        val cp = ((progress.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
        LazyColumn(Modifier.fillMaxSize().graphicsLayer {
            alpha = cp
            val sc = 1.14f - 0.14f * cp
            scaleX = sc; scaleY = sc
            translationY = (1f - cp) * 48.dp.toPx()
            renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && (1f - cp) * 18f > 0.5f)
                android.graphics.RenderEffect.createBlurEffect((1f - cp) * 18f, (1f - cp) * 18f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
            else null
        }, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { content() }
        }
    }
}

@Composable
private fun AttendanceSection(ui: UiState) {
    val att = ui.attendance
    if (att == null) { Text("No attendance data yet - pull to refresh."); return }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Today", style = MaterialTheme.typography.titleSmall)
            att.today?.sessions?.forEach { s ->
                Text("${s.session}: ${s.mark?.name ?: "?"}" + if (s.minutes_late > 0) " (${s.minutes_late} min late)" else "")
            }
            val p = att.statutory.sumOf { it.values["present"] ?: 0 }
            val a = att.statutory.sumOf { (it.values["absent"] ?: 0) + (it.values["unauthorised"] ?: 0) }
            if (p + a > 0) {
                Spacer(Modifier.height(8.dp))
                Text("%.1f%% present this year".format(100.0 * p / (p + a)), fontWeight = FontWeight.Bold)
            }
        }
    }
    if (att.lesson.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("By subject", style = MaterialTheme.typography.titleSmall)
                att.lesson.forEach { l ->
                    val lp = l.values["present"] ?: 0
                    val la = (l.values["absent"] ?: 0) + (l.values["unauthorised"] ?: 0)
                    val late = l.values["late"] ?: 0
                    Text("${l.subject}: $lp present, $la absent" + if (late > 0) ", $late late" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    val exceptions = att.statutory.flatMap { it.exceptions }
    if (exceptions.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Absences", style = MaterialTheme.typography.titleSmall)
                exceptions.forEach { e ->
                    Text("${e.date}  ${e.period}  ${e.description.ifBlank { "Absence" }}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NoticesSection(ui: UiState, vm: AppViewModel) {
    val nb = ui.notices
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (nb == null || nb.noticeboard.isEmpty()) { Text("No notices yet - pull to refresh."); return }
    Column {
        nb.noticeboard.forEach { n ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = !busy && n.idString().isNotEmpty()) {
                busy = true
                error = null
                vm.openNoticeFile(n.idString()) { ok, payload, err ->
                    busy = false
                    if (ok && payload != null) {
                        val uri = android.net.Uri.parse(payload.substringBefore("|"))
                        val mime = payload.substringAfter("|", "*/*")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { vm.getApplication<android.app.Application>().startActivity(intent) }
                            .onFailure { error = "no app can open this file" }
                    } else {
                        error = err
                    }
                }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(n.name, fontWeight = FontWeight.SemiBold)
                    n.filename?.let { f ->
                        Text(f + (n.filesize?.let { " - ${it / 1024} KB" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (busy) Text("Downloading...", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ExamsSection(ui: UiState) {
    val ex = ui.exams
    if (ex == null) { Text("No exams data yet - pull to refresh."); return }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            ex.countdown?.let { Text("Next exam: ${it.exam} in ${it.minutes_to_go / 1440} days", fontWeight = FontWeight.Bold) }
            ex.timetable.forEach { e ->
                Text("${e.datetime}  ${e.title}  ${e.room ?: ""} ${e.seat ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            ex.results.forEach { r ->
                Text("${r.date}  ${r.title}: ${r.result}", style = MaterialTheme.typography.bodySmall)
            }
            if (ex.timetable.isEmpty() && ex.results.isEmpty() && ex.countdown == null) Text("No exams recorded.")
        }
    }
}

@Composable
private fun CalendarSection(ui: UiState) {
    val cal = ui.calendar
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (cal == null || cal.calendar.isEmpty()) { Text("No events yet - pull to refresh.") }
            else cal.calendar.forEach { e ->
                Text("${e.start_date.take(10)}  ${e.description}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ClubsSection(ui: UiState) {
    val clubs = ui.clubs
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (clubs == null || clubs.clubs.isEmpty()) { Text("No clubs yet - pull to refresh.") }
            else clubs.clubs.forEach { c ->
                Text(c.name, fontWeight = FontWeight.SemiBold)
                c.description?.let { Text(stripHtml(it), style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}


private val PROFILE_FIELDS = listOf(
    "forename" to "Forename",
    "surname" to "Surname",
    "date_of_birth" to "Date of birth",
    "gender" to "Gender",
    "email" to "Email",
    "phone" to "Phone",
    "address" to "Address",
    "post_code" to "Postcode",
    "year_group" to "Year group",
    "form_group" to "Form group",
    "house_group" to "House group",
    "admission_date" to "Admission date",
    "admission_number" to "Admission number",
    "exam_candidate_number" to "Exam candidate number",
    "unique_learner_number" to "Unique learner number",
    "unique_pupil_number" to "Unique pupil number"
)

/** Render one personal field. Objects (form/house group) become readable text; nulls drop out. */
private fun renderPersonalValue(v: kotlinx.serialization.json.JsonElement?): String? {
    if (v == null || v is kotlinx.serialization.json.JsonNull) return null
    return when (v) {
        is kotlinx.serialization.json.JsonPrimitive -> {
            val t = v.content.trim()
            if (t.isEmpty() || t == "null") null else t
        }
        is kotlinx.serialization.json.JsonObject -> {
            val name = (v["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val emp = (v["employee"] as? kotlinx.serialization.json.JsonObject)?.let { e ->
                listOf(
                    (e["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
                    (e["forename"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
                    (e["surname"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                ).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
            }
            val room = (v["room"] as? kotlinx.serialization.json.JsonObject)?.let {
                (it["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
            listOfNotNull(name, emp, room?.let { "($it)" }).joinToString(" - ").ifBlank { null }
        }
        else -> null
    }
}

@Composable
fun ProfileTab(ui: UiState, vm: AppViewModel) {
    val account = ui.accounts.firstOrNull { it.key == ui.currentAccount }
    val avatar = remember(ui.currentAccount) {
        vm.avatarFile(ui.currentAccount)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) {
                        Image(bitmap = avatar, contentDescription = "Profile photo", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(account?.display_name ?: "", style = MaterialTheme.typography.titleLarge)
                Text(account?.school ?: "", style = MaterialTheme.typography.bodySmall)
                Text(account?.username ?: "", style = MaterialTheme.typography.labelSmall)
            }
        }
        val personal = ui.personal?.personal
        if (personal == null) {
            item { Text("No profile data cached yet. Pull to refresh.") }
        } else {
            PROFILE_FIELDS.forEach { (key, label) ->
                val value = renderPersonalValue(personal[key])
                if (value != null) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isDemo(ui: UiState): Boolean = ui.currentAccount?.contains("demo.offline") == true

private fun matchSeatingPlan(ui: UiState, group: String): SeatingPlanEntry? {
    val plans = ui.seatingPlans?.seating_plans ?: return null
    return plans.firstOrNull { it.group_name.equals(group, true) }
        ?: plans.firstOrNull { group.isNotBlank() && it.group_name.contains(group, true) }
        ?: if (isDemo(ui)) plans.firstOrNull() else null
}

private fun matchRegister(ui: UiState, group: String): RegisterEntry? {
    val regs = ui.registers?.registers ?: return null
    return regs.firstOrNull { group.isNotBlank() && it.name.contains(group, true) }
        ?: if (isDemo(ui)) regs.firstOrNull() else null
}

@Composable
fun LessonSheet(ui: UiState, vm: AppViewModel, lesson: Lesson) {
    val group = lesson.teaching_group?.name ?: ""
    val planEntry = remember(group) { matchSeatingPlan(ui, group) }
    val regEntry = remember(group) { matchRegister(ui, group) }
    var reg by remember { mutableStateOf<RegisterDetail?>(null) }
    var plan by remember { mutableStateOf<SeatingPlanDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(group) {
        regEntry?.let { vm.registerDetail(it.idString()) { d, e -> reg = d; if (d == null) err = e } }
        planEntry?.let { vm.seatingPlanDetail(it) { d, e -> plan = d; if (d == null && err == null) err = e } }
        if (regEntry == null && planEntry == null) err = "no register or seating plan cached for this class"
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
        Text(lesson.subjectName().ifBlank { group }, style = MaterialTheme.typography.titleLarge)
        Text("$group  ${lesson.room?.name ?: ""}  ${lesson.teacherName()}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        plan?.let {
            Text("Seating plan - ${it.room.long_name.ifBlank { it.room.short_name }}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            SeatingPlanCanvas(it, reg?.learners ?: emptyList())
            Spacer(Modifier.height(12.dp))
        }
        reg?.let { RegisterDetailView(it) }
        if (reg == null && plan == null) {
            Text(err ?: "Loading...", style = MaterialTheme.typography.bodyMedium,
                color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun RegisterDetailView(detail: RegisterDetail) {
    Text("Class register" + if (detail.date.isNotBlank()) " - ${detail.date}" else "", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    val present = detail.learners.count { l -> l.marks.any { it.mark == "/" || it.mark == "\\" } }
    Text("$present of ${detail.learners.size} marked present", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    detail.learners.sortedBy { it.surname }.forEach { l ->
        val marks = l.marks.mapNotNull { it.mark.ifBlank { null } }.joinToString(" ")
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(l.fullName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (l.has_detention) Text("D ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            if (l.on_report) Text("R ", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            Text(marks, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Room layout: blackboard + tables, seats labelled with learner initials. */
@Composable
fun SeatingPlanCanvas(plan: SeatingPlanDetail, learners: List<RegisterLearner>) {
    val byId = learners.associateBy({ it.idString() }, { l -> l.forename.take(1) + l.surname.take(1) })
    val room = plan.room
    if (room.width <= 0 || room.height <= 0) { Text("No room layout in this plan."); return }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(280.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFEFF3F7))
    ) {
        val sx = maxWidth.value / room.width
        val sy = 280.0 / room.height
        val s = minOf(sx, sy)
        room.blackboard?.let { bb ->
            Box(
                Modifier
                    .offset(x = (bb.x * s).dp, y = (bb.y * s).dp)
                    .width(((if (room.width < 3 || room.height < 3) 2.0 else 2.5) * s).dp)
                    .height(((if (room.width < 6 && room.height < 6) 0.1 else 0.2) * s).dp)
                    .graphicsLayer { rotationZ = bb.angle.toFloat() }
                    .background(Color(0xFF37474F))
            )
        }
        room.tables.forEach { t ->
            val w = (if (t.type.contains("2")) 1.2 else 0.6) * s
            val h = 0.6 * s
            // Vendor coordinates are the rotated bounding box's top-left.
            // Compose rotates around the centre, so undo its half-difference.
            val quarterTurn = ((t.angle.toInt() % 360) + 360) % 360 in listOf(90, 270)
            val delta = if (quarterTurn) kotlin.math.abs(w - h) / 2 else 0.0
            Box(
                Modifier
                    .offset(x = (t.x * s - delta).dp, y = (t.y * s + delta).dp)
                    .width(w.dp).height(h.dp)
                    .graphicsLayer { rotationZ = t.angle.toFloat() }
                    .background(Color(0xFF90A4AE), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                val label = listOfNotNull(t.left, t.right).mapNotNull { byId[it] }.joinToString(" ")
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.graphicsLayer { rotationZ = -t.angle.toFloat() })
            }
        }
    }
}

@Composable
private fun MessagesSection(ui: UiState) {
    var sentTab by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !sentTab, onClick = { sentTab = false }, label = { Text("Received") })
        FilterChip(selected = sentTab, onClick = { sentTab = true }, label = { Text("Sent") })
    }
    Spacer(Modifier.height(8.dp))
    val data = if (sentTab) ui.outbox else ui.inbox
    if (data == null) { Text("No messages cached yet - pull to refresh."); return }
    if (data.messages.isEmpty()) { Text(if (sentTab) "Nothing sent." else "Inbox zero."); return }
    data.messages.forEach { m ->
        var open by remember(m.subject + m.date) { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { open = !open }) {
            Column(Modifier.padding(12.dp)) {
                Text(m.subject.ifBlank { "(no subject)" }, fontWeight = FontWeight.SemiBold)
                Text("${m.sender?.name ?: ""}  ${m.date}", style = MaterialTheme.typography.labelSmall)
                if (open) {
                    Spacer(Modifier.height(6.dp))
                    Text(stripHtml(m.body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RegistersSection(ui: UiState, vm: AppViewModel) {
    val regs = ui.registers?.registers
    if (regs == null) { Text("No registers cached yet - pull to refresh."); return }
    var picked by remember { mutableStateOf<RegisterEntry?>(null) }
    var detail by remember { mutableStateOf<RegisterDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    if (picked == null) {
        regs.forEach { r ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                picked = r; detail = null; err = null
                vm.registerDetail(r.idString()) { d, e -> detail = d; err = e }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.name, fontWeight = FontWeight.SemiBold)
                    Text("${r.start_time} - ${r.end_time}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    } else {
        TextButton(onClick = { picked = null }) { Text("< All registers") }
        when {
            detail != null -> RegisterDetailView(detail!!)
            err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
            else -> Text("Loading...")
        }
    }
}

@Composable
private fun SeatingPlansSection(ui: UiState, vm: AppViewModel) {
    val plans = ui.seatingPlans?.seating_plans
    if (plans == null) { Text("No seating plans cached yet - pull to refresh."); return }
    var picked by remember { mutableStateOf<SeatingPlanEntry?>(null) }
    var detail by remember { mutableStateOf<SeatingPlanDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    if (picked == null) {
        plans.forEach { p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                picked = p; detail = null; err = null
                vm.seatingPlanDetail(p) { d, e -> detail = d; err = e }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.group_name, fontWeight = FontWeight.SemiBold)
                    Text("${p.teacher_name}  ${p.room_name}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    } else {
        TextButton(onClick = { picked = null }) { Text("< All plans") }
        when {
            detail != null -> SeatingPlanCanvas(detail!!, emptyList())
            err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
            else -> Text("Loading...")
        }
    }
}

@Composable
private fun CateringSection(ui: UiState) {
    val c = ui.catering
    if (c == null) { Text("No catering data yet - pull to refresh."); return }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Balance", style = MaterialTheme.typography.titleSmall)
            Text("GBP ${c.balanceText()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
    c.transactions.forEach { t ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(t.date, style = MaterialTheme.typography.labelSmall)
                t.items.forEach { i ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(i.item, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(i.priceText(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (t.balanceText().isNotBlank()) Text("balance after: ${t.balanceText()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DocumentsSection(ui: UiState) {
    val docs = ui.documents?.documents
    if (docs == null) { Text("No documents cached yet - pull to refresh."); return }
    if (docs.isEmpty()) { Text("No documents."); return }
    docs.forEach { d ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(d.summary ?: d.type ?: "Document", fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(d.type, d.status, d.last_updated).joinToString("  "), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LinksSection(ui: UiState) {
    val links = ui.externalLinks?.links
    if (links == null) { Text("No links cached yet - pull to refresh."); return }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    links.forEach { l ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(l.url))) }
        }) {
            Column(Modifier.padding(12.dp)) {
                Text(l.name, fontWeight = FontWeight.SemiBold)
                Text(l.url, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MedicalSection(ui: UiState) {
    val m = ui.medical?.medical
    if (m == null) { Text("No medical data yet - pull to refresh."); return }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Emergency consent: " + if (m.have_emergency_consent) "yes" else "no", style = MaterialTheme.typography.bodyMedium)
            if (m.dietary_needs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Dietary needs", style = MaterialTheme.typography.titleSmall)
                m.dietary_needs.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (m.medical_notes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                m.medical_notes.forEach { Text("- ${it.type}: ${it.summary}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun TeachersSection(ui: UiState) {
    val teachers = ui.teachers?.teachers
    if (teachers == null) { Text("No staff directory cached yet - pull to refresh."); return }
    teachers.forEach { t ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(t.fullName(), fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(t.email, t.phone).joinToString("  "), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Nav bar item; selection is shown by the shared sliding glass pill
 * that floats above the bar, so the item itself keeps a transparent
 * indicator and the stock icon/label look. */
@Composable
fun RowScope.GlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        modifier = modifier
    )
}
