package dev.edulinktwo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    BackHandler(enabled = !anySheet && !accountSheet && ui.tab != Tab.HOMEWORK) { vm.selectTab(Tab.HOMEWORK) }

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
    val barElev = if (scrollPx > 6f) 6.dp else 0.dp

    Box(Modifier.fillMaxSize().nestedScroll(scrollProbe)) {
        // Layer 1 (deep): barely-there gradient + faint doodle texture.
        // Perspective stack (calibration: the "Perspective" wallpaper app at
        // ~70-80%): rotation-driven, layers at increasing depth amplitudes,
        // background also rotates a touch for the 3D read.
        val drift = Modifier.graphicsLayer {
            scaleX = 1.2f; scaleY = 1.2f
            rotationZ = tilt.x * 1.4f
            translationY = -scrollPx * 0.18f + tilt.y * 12.dp.toPx()
            translationX = tilt.x * 16.dp.toPx()
        }
        val contentDrift = Modifier.graphicsLayer {
            translationY = tilt.y * 16.dp.toPx()
            translationX = tilt.x * 26.dp.toPx()
        }
        val nearDrift = Modifier.graphicsLayer {
            translationY = tilt.y * 20.dp.toPx()
            translationX = tilt.x * 34.dp.toPx()
        }
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

        // Layers 2+3: content scrolls between the pinned bars.
        Column(Modifier.fillMaxSize().then(contentDrift)) {
            TopAppBar(
                modifier = Modifier.then(nearDrift).shadow(barElev),
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
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f)
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
        NavigationBar(modifier = Modifier.then(nearDrift).shadow(barElev)) {
            NavigationBarItem(ui.tab == Tab.HOMEWORK, { vm.selectTab(Tab.HOMEWORK) }, { Icon(Icons.Default.Home, null) }, label = { Text("Homework") })
            NavigationBarItem(ui.tab == Tab.TIMETABLE, { vm.selectTab(Tab.TIMETABLE) }, { Icon(Icons.Default.DateRange, null) }, label = { Text("Timetable") })
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ProfileNavButton(ui, vm, onTap = { vm.selectTab(Tab.PROFILE) }, onLong = { accountSheet = true })
            }
            NavigationBarItem(ui.tab == Tab.POINTS, { vm.selectTab(Tab.POINTS) }, { Icon(Icons.Default.Star, null) }, label = { Text("Points") })
            NavigationBarItem(ui.tab == Tab.MORE, { vm.selectTab(Tab.MORE) }, { Icon(Icons.Default.Menu, null) }, label = { Text("More") })
        }
        }
    }

    if (detail != null || detailLoading || detailError != null) {
        ModalBottomSheet(
            onDismissRequest = { detail = null; detailLoading = false; detailError = null }
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
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
        ModalBottomSheet(onDismissRequest = { lessonSel = null }) {
            LessonSheet(ui, vm, lesson)
        }
    }

    if (accountSheet) {
        ModalBottomSheet(onDismissRequest = { accountSheet = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
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
fun ProfileNavButton(ui: UiState, vm: AppViewModel, onTap: () -> Unit, onLong: () -> Unit) {
    val avatar = remember(ui.currentAccount) {
        vm.avatarFile(ui.currentAccount)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .offset(y = (-8).dp)
            .size(56.dp)
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
                days != null && days <= 0 -> Color(0xFFFFCDD2) // due today/overdue: red
                days == 1 -> Color(0xFFFFE0B2) // due tomorrow: orange
                else -> Color(0xFFECEFF1) // 2+ days: grey
            }
            val dueLabel = when {
                days == null -> "due ${h.due_date}"
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
        items(a.achievement, key = { "ach-" + it.date + (it.comments ?: "") }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("+${e.points}  ${e.comments ?: ""}")
                    Text(e.date, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Behaviour", style = MaterialTheme.typography.titleMedium) }
        items(b.behaviour, key = { "beh-" + it.date + (it.comments ?: "") }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("-${e.points}  ${e.comments ?: ""}")
                    Text("${e.date}  ${e.lesson_information ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (b.detentions.isNotEmpty()) {
            item { Text("Detentions", style = MaterialTheme.typography.titleMedium) }
            items(b.detentions) { d ->
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
    BackHandler(enabled = section != null) { section = null }
    when (section) {
        null -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Attendance", "Notices", "Exams", "Calendar", "Clubs", "Messages", "Registers", "Seating Plans", "Catering", "Documents", "Links", "Medical", "Teachers").forEach { name ->
                    item {
                        Card(Modifier.fillMaxWidth().clickable { section = name }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< More") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    .offset(x = ((bb.x - 0.8) * s).dp, y = (bb.y * s).dp)
                    .width((1.6 * s).dp).height(6.dp)
                    .graphicsLayer { rotationZ = bb.angle.toFloat() }
                    .background(Color(0xFF37474F))
            )
        }
        room.tables.forEach { t ->
            val w = (if (t.type.contains("2")) 1.2 else 0.6) * s
            val h = 0.6 * s
            Box(
                Modifier
                    .offset(x = (t.x * s).dp, y = (t.y * s).dp)
                    .width(w.dp).height(h.dp)
                    .graphicsLayer { rotationZ = t.angle.toFloat() }
                    .background(Color(0xFF90A4AE), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                val label = listOfNotNull(t.left, t.right).mapNotNull { byId[it] }.joinToString(" ")
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
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
