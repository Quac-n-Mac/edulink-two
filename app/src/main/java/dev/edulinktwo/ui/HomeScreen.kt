package dev.edulinktwo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edulinktwo.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(ui: UiState, vm: AppViewModel) {
    var accountMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<HomeworkDetail?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
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
                    Box {
                        IconButton(onClick = { accountMenu = true }) { Icon(Icons.Default.AccountCircle, "Accounts") }
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            ui.accounts.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text("${a.display_name} (${a.username})" + if (a.key == ui.currentAccount) "  ✓" else "") },
                                    onClick = { accountMenu = false; if (a.key != ui.currentAccount) vm.switchAccount(a.key) },
                                    trailingIcon = {
                                        IconButton(onClick = { vm.removeAccount(a.key); accountMenu = false }) {
                                            Icon(Icons.Default.Delete, "Remove")
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(text = { Text("+ Add account") }, onClick = {
                                accountMenu = false
                                vm.showAddAccount()
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(ui.tab == Tab.HOMEWORK, { vm.selectTab(Tab.HOMEWORK) }, { Icon(Icons.Default.Home, null) }, label = { Text("Homework") })
                NavigationBarItem(ui.tab == Tab.TIMETABLE, { vm.selectTab(Tab.TIMETABLE) }, { Icon(Icons.Default.DateRange, null) }, label = { Text("Timetable") })
                NavigationBarItem(ui.tab == Tab.MESSAGES, { vm.selectTab(Tab.MESSAGES) }, { Icon(Icons.Default.Email, null) }, label = { Text("Messages") })
                NavigationBarItem(ui.tab == Tab.POINTS, { vm.selectTab(Tab.POINTS) }, { Icon(Icons.Default.Star, null) }, label = { Text("Points") })
                NavigationBarItem(ui.tab == Tab.MORE, { vm.selectTab(Tab.MORE) }, { Icon(Icons.Default.Menu, null) }, label = { Text("More") })
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            // Fast transitions: 120ms, no slow fades.
            AnimatedContent(
                targetState = ui.tab,
                transitionSpec = { fadeIn(tween(FAST_MS)) togetherWith fadeOut(tween(FAST_MS)) },
                label = "tab"
            ) { tab ->
                when (tab) {
                    Tab.HOMEWORK -> HomeworkTab(ui, vm) { detail = it }
                    Tab.TIMETABLE -> TimetableTab(ui)
                    Tab.MESSAGES -> MessagesTab(ui)
                    Tab.POINTS -> PointsTab(ui)
                    Tab.MORE -> MoreTab(ui)
                }
            }
        }
    }

    detail?.let { d ->
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
            title = { Text(d.homework.activity) },
            text = {
                Column {
                    Text("${d.homework.subject} - due ${d.homework.due_date}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stripHtml(d.homework.description))
                    if (d.homework.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        d.homework.attachments.forEach {
                            Text("Attachment: ${it.filename ?: ""} (not downloaded)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        )
    }
}

fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

@Composable
fun HomeworkTab(ui: UiState, vm: AppViewModel, onDetail: (HomeworkDetail) -> Unit) {
    val hw = ui.homework
    if (hw == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No homework cached yet. Pull to refresh.") }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Current", style = MaterialTheme.typography.titleMedium) }
        items(hw.homework.current) { h ->
            Card(Modifier.fillMaxWidth().clickable {
                vm.homeworkDetail(h.idString()) { d, _ -> if (d != null) onDetail(d) }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(h.activity, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(h.subject, style = MaterialTheme.typography.bodySmall)
                        Text("due ${h.due_date}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (h.completed) Text("Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (hw.homework.past.isNotEmpty()) {
            item { Text("Past", style = MaterialTheme.typography.titleMedium) }
            items(hw.homework.past) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(h.activity)
                        Text("${h.subject} - was due ${h.due_date}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableTab(ui: UiState) {
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
                items(day.lessons) { l ->
                    Card(Modifier.fillMaxWidth()) {
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No points cached yet. Pull to refresh.") }
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
        items(a.achievement) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("+${e.points}  ${e.comments ?: ""}")
                    Text(e.date, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Behaviour", style = MaterialTheme.typography.titleMedium) }
        items(b.behaviour) { e ->
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
fun MessagesTab(ui: UiState) {
    val inbox = ui.inbox
    var open by remember { mutableStateOf<Message?>(null) }
    if (inbox == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No messages cached yet. Pull to refresh.") }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(inbox.messages) { m ->
            val unread = m.read.isNullOrBlank()
            Card(Modifier.fillMaxWidth().clickable { open = m }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((if (unread) "● " else "") + m.subject, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
                        Text(m.date, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(m.sender?.name ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Reading here never marks a message read in Edulink.", style = MaterialTheme.typography.labelSmall) }
    }
    open?.let { m ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            title = { Text(m.subject) },
            text = {
                Column {
                    Text("${m.sender?.name ?: ""} - ${m.date}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stripHtml(m.body))
                }
            }
        )
    }
}

@Composable
fun MoreTab(ui: UiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    ui.personal?.personal?.entries?.take(10)?.forEach { (k, v) ->
                        Text("${k.replace('_', ' ')}: ${v.toString().trim('"')}", style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("No profile cached yet.")
                }
            }
        }
        item { Text("Attendance", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val att = ui.attendance
                    if (att == null) { Text("No attendance cached yet.") }
                    else {
                        att.today?.sessions?.forEach { s ->
                            Text("${s.session}: ${s.mark?.name ?: "?"}" + if (s.minutes_late > 0) " (${s.minutes_late} min late)" else "")
                        }
                        val p = att.statutory.sumOf { it.values["present"] ?: 0 }
                        val a = att.statutory.sumOf { (it.values["absent"] ?: 0) + (it.values["unauthorised"] ?: 0) }
                        if (p + a > 0) {
                            Text("%.1f%% present".format(100.0 * p / (p + a)), fontWeight = FontWeight.Bold)
                        }
                        att.lesson.take(8).forEach { l ->
                            val lp = l.values["present"] ?: 0
                            val la = (l.values["absent"] ?: 0) + (l.values["unauthorised"] ?: 0)
                            Text("${l.subject}: $lp present, $la absent", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text("Exams", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val ex = ui.exams
                    if (ex == null) { Text("No exams cached yet.") }
                    else {
                        ex.countdown?.let { Text("Next exam: ${it.exam} in ${it.minutes_to_go / 1440} days", fontWeight = FontWeight.Bold) }
                        ex.timetable.forEach { e ->
                            Text("${e.datetime}  ${e.title}  ${e.room ?: ""} ${e.seat ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        ex.results.forEach { r ->
                            Text("${r.date}  ${r.title}: ${r.result}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text("Calendar", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val cal = ui.calendar
                    if (cal == null || cal.calendar.isEmpty()) { Text("No events cached yet.") }
                    else cal.calendar.take(15).forEach { e ->
                        Text("${e.start_date.take(10)}  ${e.description}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { Text("Notices", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val nb = ui.notices
                    if (nb == null || nb.noticeboard.isEmpty()) { Text("No notices cached yet.") }
                    else {
                        nb.noticeboard.forEach { n -> Text("${n.name} (${n.filename ?: ""})", style = MaterialTheme.typography.bodyMedium) }
                        Text("Documents are listed only, never auto-downloaded.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { Text("Clubs", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val clubs = ui.clubs
                    if (clubs == null || clubs.clubs.isEmpty()) { Text("No clubs cached yet.") }
                    else clubs.clubs.forEach { c ->
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        c.description?.let { Text(stripHtml(it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
