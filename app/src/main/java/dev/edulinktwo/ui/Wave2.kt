package dev.edulinktwo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.edulinktwo.*
import kotlinx.serialization.json.JsonPrimitive

/** Every section here loads lazily on first open and shows the server's
 * own error text when a surface is unavailable. */

@Composable
fun FormsSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<FormsData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<FormEntry?>(null) }
    var detail by remember { mutableStateOf<FormDetailData?>(null) }
    var detailErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.forms { d, e -> data = d; err = e } }
    // Back walks one level: open form -> forms list, not out to More.
    BackHandler(enabled = picked != null) { picked = null; detail = null }
    if (picked == null) {
        when {
            data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
            err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
            data!!.forms.isEmpty() -> Text("No forms.")
            else -> data!!.forms.forEach { f ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    picked = f; detail = null; detailErr = null
                    vm.formDetail(f.id) { d, e -> detail = d; detailErr = e }
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(f.subject, fontWeight = FontWeight.SemiBold)
                        Text(
                            "due ${f.due}" + if (f.submitted != null) "  -  submitted ${f.submitted}" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    } else {
        TextButton(onClick = { picked = null }) { Text("< All forms") }
        when {
            detail != null -> {
                val form = detail!!.form
                Text(form.subject, style = MaterialTheme.typography.titleMedium)
                Text("due ${form.due}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                form.fields.forEach { field ->
                    if (field.type == "subheading")
                        Text(fieldText(field), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                    else
                        Text(fieldText(field) + if (field.required) " *" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            detailErr != null -> Text(detailErr!!, color = MaterialTheme.colorScheme.error)
            else -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun fieldText(f: FormField): String = when (val c = f.content) {
    null -> ""
    is JsonPrimitive -> if (c.isString) c.content else c.toString()
    else -> c.toString()
}

@Composable
fun AbsencesSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<AbsencesData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.absences { d, e -> data = d; err = e } }
    when {
        data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
        data!!.absences.isEmpty() -> Text("No unexplained absences.")
        else -> data!!.absences.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("AM: ${a.marks.am}   PM: ${a.marks.pm}", fontWeight = FontWeight.SemiBold)
                    a.log.forEach { l ->
                        Text("${l.time}  ${l.message}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DetentionsSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<DetentionsData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<DetentionSessionEntry?>(null) }
    var detail by remember { mutableStateOf<DetentionDetailData?>(null) }
    var detailErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.detentions { d, e -> data = d; err = e } }
    // Back walks one level: open session -> sessions list, not out to More.
    BackHandler(enabled = picked != null) { picked = null; detail = null }
    if (picked == null) {
        when {
            data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
            err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
            data!!.sessions.isEmpty() -> Text("No detentions.")
            else -> data!!.sessions.forEach { s ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    picked = s; detail = null; detailErr = null
                    vm.detentionDetail(s.id) { d, e -> detail = d; detailErr = e }
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.description, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${s.date}  ${s.start_time} - ${s.end_time}  ${s.location}" +
                                (s.num_of_detainees?.let { "  ($it attending)" } ?: ""),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    } else {
        TextButton(onClick = { picked = null }) { Text("< All detentions") }
        when {
            detail != null -> {
                val s = detail!!.session
                Text(s.description, style = MaterialTheme.typography.titleMedium)
                Text("${s.date}  ${s.start_time} - ${s.end_time}  ${s.location}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                if (s.students.isEmpty()) Text("No students listed.")
                s.students.forEach { st ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(st.attended.ifBlank { "listed" }, fontWeight = FontWeight.SemiBold)
                            if (st.incident_type.isNotBlank())
                                Text("${st.incident_type}  ${st.incident_date}", style = MaterialTheme.typography.bodySmall)
                            if (st.staff_comments.isNotBlank())
                                Text(st.staff_comments, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            detailErr != null -> Text(detailErr!!, color = MaterialTheme.colorScheme.error)
            else -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MomentsSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<MomentsData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.moments { d, e -> data = d; err = e } }
    when {
        data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
        data!!.moments.isEmpty() -> Text("No moments yet.")
        else -> data!!.moments.forEach { m ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    m.attachments.filter { it.mime_type.startsWith("image/") }.forEach { att ->
                        AsyncImage(
                            model = att.url,
                            contentDescription = att.filename,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(top = 8.dp)
                        )
                    }
                    m.attachments.filter { !it.mime_type.startsWith("image/") }.forEach { att ->
                        Text(att.filename, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (m.created.isNotBlank()) Text(m.created, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private fun cellText(c: AssessmentCell): String = when (val v = c.content) {
    null -> ""
    is JsonPrimitive -> if (v.isString) v.content else v.toString()
    else -> v.toString()
}

private fun cssColor(css: String): Color? =
    Regex("#([0-9a-fA-F]{6})").find(css)?.let { m ->
        runCatching { Color(android.graphics.Color.parseColor("#" + m.groupValues[1])) }.getOrNull()
    }

@Composable
fun AssessmentSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<AssessmentData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.assessment { d, e -> data = d; err = e } }
    when {
        data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
        data!!.assessment.isEmpty() -> Text("No assessment grids.")
        else -> data!!.assessment.forEach { g ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(g.name + if (g.applies_this_year) " (this year)" else "", fontWeight = FontWeight.SemiBold)
                    val head = stripHtml(g.header).trim()
                    if (head.isNotBlank()) Text(head, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    // Wide grids scroll sideways; cells keep the grid's own colours.
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            g.resultstable.forEach { row ->
                                val cells = row.filter { it.visible }
                                if (cells.any { cellText(it).isNotBlank() }) {
                                    Row {
                                        cells.forEach { c ->
                                            val bg = cssColor(c.css)
                                            Text(
                                                cellText(c),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .then(if (bg != null) Modifier.background(bg) else Modifier)
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val foot = stripHtml(g.footer).trim()
                    if (foot.isNotBlank()) Text(foot, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun ReportCardSection(vm: AppViewModel) {
    var data by remember { mutableStateOf<ReportCardData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.reportCard { d, e -> data = d; err = e } }
    when {
        data == null && err == null -> Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        err != null -> Text(err!!, color = MaterialTheme.colorScheme.error)
        else -> {
            val card = data!!.card
            Text("${card.start_date} to ${card.end_date}" + if (card.current) " (current)" else "", fontWeight = FontWeight.SemiBold)
            if (card.comments.isNotBlank()) Text(card.comments, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            card.days.forEach { day ->
                Text(day.date, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                day.classes.forEach { c ->
                    val hit = c.targets.count { it.achieved }
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                listOf(c.label, c.initials).filter { it.isNotBlank() }.joinToString("  ") +
                                    if (c.targets.isNotEmpty()) "  targets $hit/${c.targets.size}" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (c.comments.isNotBlank()) Text(c.comments, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
