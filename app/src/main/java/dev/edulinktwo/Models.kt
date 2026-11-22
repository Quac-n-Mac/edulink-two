package dev.edulinktwo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

val json = Json { ignoreUnknownKeys = true }

@Serializable
data class Id(val value: String)

@Serializable
data class Employee(
    val id: JsonElement? = null,
    val title: String = "",
    val forename: String = "",
    val surname: String = ""
) {
    fun display(): String = listOf(title, "$forename $surname".trim()).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class Attachment(
    val id: JsonElement? = null,
    val kind: String? = null,
    val filename: String? = null,
    val mime_type: String? = null
) {
    fun idString(): String = id?.toString()?.trim('"') ?: ""
}

@Serializable
data class AttachmentResult(
    val ok: Boolean = false,
    val error: String? = null,
    val filename: String? = null,
    val mime_type: String? = null,
    val data_b64: String? = null
)

@Serializable
data class HomeworkItem(
    val id: JsonElement? = null,
    val activity: String = "",
    val subject: String = "",
    val due_date: String = "",
    val completed: Boolean = false,
    val status: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val source: String? = null
) {
    fun idString(): String = id?.toString()?.trim('"') ?: ""
}

@Serializable
data class HomeworkLists(val current: List<HomeworkItem> = emptyList(), val past: List<HomeworkItem> = emptyList())
@Serializable
data class HomeworkData(val homework: HomeworkLists = HomeworkLists())

@Serializable
data class HomeworkDetailInner(
    val activity: String = "",
    val subject: String = "",
    val description: String = "",
    val due_date: String = "",
    val attachments: List<Attachment> = emptyList()
)
@Serializable
data class HomeworkDetail(val homework: HomeworkDetailInner = HomeworkDetailInner())

@Serializable
data class Room(val name: String? = null)
@Serializable
data class TeachingGroup(val name: String? = null, val subject: String? = null)
@Serializable
data class Lesson(
    val room: Room? = null,
    val teaching_group: TeachingGroup? = null,
    val subject: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val period_id: JsonElement? = null,
    val teachers: JsonElement? = null
) {
    fun teacherName(): String = teachers?.toString()?.trim('"') ?: ""
    fun subjectName(): String = subject ?: teaching_group?.subject ?: ""
}

@Serializable
data class TimetablePeriod(val id: JsonElement? = null, val name: String? = null, val start_time: String? = null, val end_time: String? = null)
@Serializable
data class Day(val date: String, val name: String? = null, val lessons: List<Lesson> = emptyList(), val periods: List<TimetablePeriod> = emptyList()) {
    fun orderedLessons(): List<Lesson> = lessonsChronologically(lessons.map { lesson ->
        val key = lesson.period_id?.toString()?.trim('"')
        val period = periods.firstOrNull { key != null && (it.id?.toString()?.trim('"') == key || it.name == key) }
        lesson.copy(start_time = lesson.start_time?.takeIf { it.isNotBlank() } ?: period?.start_time,
            end_time = lesson.end_time?.takeIf { it.isNotBlank() } ?: period?.end_time)
    }) { it.start_time }
}
@Serializable
data class Week(val name: String? = null, val days: List<Day> = emptyList())
@Serializable
data class TimetableData(val weeks: List<Week> = emptyList())

@Serializable
data class AchievementEvent(val date: String = "", val comments: String? = null, val points: Long = 0)
@Serializable
data class AchievementData(val achievement: List<AchievementEvent> = emptyList(), val employees: List<Employee> = emptyList())

@Serializable
data class BehaviourEvent(
    val date: String = "",
    val comments: String? = null,
    val points: Long = 0,
    val lesson_information: String? = null
)
@Serializable
data class Detention(
    val description: String? = null,
    val date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val location: String? = null,
    val attended: String? = null
)
@Serializable
data class BehaviourData(
    val behaviour: List<BehaviourEvent> = emptyList(),
    val detentions: List<Detention> = emptyList(),
    val employees: List<Employee> = emptyList()
)

@Serializable
data class Account(
    val key: String,
    val username: String,
    val display_name: String,
    val school: String,
    val host: String,
    val school_id: Long = 0,
    val school_code: String = ""
)

/** Result of the pre-login school resolution (School.FromCode + SchoolDetails). */
@Serializable
data class SchoolResolveResult(
    val ok: Boolean = false,
    val error: String? = null,
    val host: String = "",
    val school_id: Long = 0,
    val name: String = "",
    val logo_b64: String? = null
)
@Serializable
data class AccountsResult(val ok: Boolean = false, val current: String? = null, val accounts: List<Account> = emptyList())

@Serializable
data class ApiResult(
    val ok: Boolean = false,
    val error: String? = null,
    val key: String? = null,
    val display_name: String? = null,
    val school: String? = null,
    val surfaces_ok: Int? = null,
    val total_ms: Long? = null,
    val prefetch_ms: Long? = null,
    val failed: List<String>? = null,
    val avatar_b64: String? = null
)

/** Cached envelope written by the Rust core. `data` stays raw until decoded per surface. */
@Serializable
data class Cached(val fetched_at: String, val data: JsonObject)

fun decodeCached(raw: String): Cached? = runCatching {
    json.decodeFromString<Cached>(raw)
}.getOrNull()

@Volatile var lastDecodeError: String? = null

inline fun <reified T> decodeSurface(raw: String?): Pair<T?, String?> {
    if (raw == null) return null to null
    val cached = decodeCached(raw) ?: return null to null
    return runCatching { json.decodeFromJsonElement<T>(cached.data) }
        .onFailure { lastDecodeError = "${T::class.simpleName}: ${it.message?.take(160)}" }
        .getOrNull() to cached.fetched_at
}

@Serializable
data class Mark(val name: String = "", val present: Boolean = false)
@Serializable
data class SessionMark(val session: String = "", val date: String = "", val minutes_late: Long = 0, val mark: Mark? = null)
@Serializable
data class TodayAttendance(val sessions: List<SessionMark> = emptyList())
@Serializable
data class AttendanceException(val date: String = "", val description: String = "", val period: String = "")
@Serializable
data class StatutoryMonth(val month: String = "", val values: Map<String, Long> = emptyMap(), val exceptions: List<AttendanceException> = emptyList())
@Serializable
data class LessonAttendance(val subject: String = "", val values: Map<String, Long> = emptyMap())
@Serializable
data class AttendanceData(
    val today: TodayAttendance? = null,
    val statutory: List<StatutoryMonth> = emptyList(),
    val lesson: List<LessonAttendance> = emptyList()
)

@Serializable
data class ExamCountdown(val minutes_to_go: Long = 0, val exam: String = "")
@Serializable
data class Exam(val datetime: String = "", val code: String = "", val title: String = "", val room: String? = null, val seat: String? = null, val duration: String? = null)
@Serializable
data class ExamResult(val date: String = "", val code: String = "", val title: String = "", val result: String = "")
@Serializable
data class ExamsData(
    val countdown: ExamCountdown? = null,
    val timetable: List<Exam> = emptyList(),
    val results: List<ExamResult> = emptyList()
)

@Serializable
data class Notice(
    val id: JsonElement? = null,
    val name: String = "",
    val filename: String? = null,
    val filesize: Long? = null,
    val mime_type: String? = null
) {
    fun idString(): String = id?.toString()?.trim('"') ?: ""
}
@Serializable
data class NoticeboardData(val noticeboard: List<Notice> = emptyList())

@Serializable
data class CalendarEvent(val start_date: String = "", val description: String = "", val notes: String? = null, val all_day_event: Boolean = false)
@Serializable
data class CalendarData(val calendar: List<CalendarEvent> = emptyList())

@Serializable
data class Club(val name: String = "", val description: String? = null)
@Serializable
data class ClubsData(val clubs: List<Club> = emptyList())

@Serializable
data class PersonalData(val personal: JsonObject? = null)

@Serializable
data class MessageSender(val name: String = "")
@Serializable
data class Message(
    val id: JsonElement? = null,
    val type: String? = null,
    val subject: String = "",
    val body: String = "",
    val date: String = "",
    val read: String? = null,
    val sender: MessageSender? = null,
    val attachments: List<Attachment> = emptyList()
)
@Serializable
data class MessagesData(val messages: List<Message> = emptyList())

@Serializable
data class RegisterEntry(
    val id: JsonElement? = null,
    val name: String = "",
    val start_time: String = "",
    val end_time: String = "",
    val linked: Boolean = false
) {
    fun idString(): String = id?.toString()?.trim('"') ?: ""
}
@Serializable
data class RegistersData(val registers: List<RegisterEntry> = emptyList())

@Serializable
data class RegisterPeriod(val id: JsonElement? = null, val name: String = "", val start_time: String = "", val end_time: String = "")
@Serializable
data class RegisterMark(val period_id: JsonElement? = null, val mark: String = "", val minutes_late: Long = 0)
@Serializable
data class RegisterLearner(
    val id: JsonElement? = null,
    val forename: String = "",
    val surname: String = "",
    val has_detention: Boolean = false,
    val on_report: Boolean = false,
    val marks: List<RegisterMark> = emptyList()
) {
    fun idString(): String = id?.toString()?.trim('"') ?: ""
    fun fullName(): String = "$forename $surname".trim()
}
@Serializable
data class RegisterDetail(
    val date: String = "",
    val type: String = "",
    val periods: List<RegisterPeriod> = emptyList(),
    val learners: List<RegisterLearner> = emptyList()
)

@Serializable
data class SeatingPlanEntry(
    val group_name: String = "",
    val teacher_name: String = "",
    val room_name: String = ""
)
@Serializable
data class SeatingPlansData(val seating_plans: List<SeatingPlanEntry> = emptyList())

@Serializable
data class SeatingGroup(val name: String = "", val register_type: String = "", val type: String = "")
@Serializable
data class SeatPoint(val angle: Double = 0.0, val x: Double = 0.0, val y: Double = 0.0)
@Serializable
data class SeatTable(
    val angle: Double = 0.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val left: String? = null,
    val right: String? = null,
    val type: String = ""
)
@Serializable
data class SeatingRoom(
    val short_name: String = "",
    val long_name: String = "",
    val height: Double = 0.0,
    val width: Double = 0.0,
    val blackboard: SeatPoint? = null,
    val tables: List<SeatTable> = emptyList()
)
@Serializable
data class SeatingPlanDetail(val group: SeatingGroup = SeatingGroup(), val room: SeatingRoom = SeatingRoom())

@Serializable
data class CateringItem(val item: String = "", val price: JsonElement? = null) {
    fun priceText(): String = price?.toString()?.trim('"') ?: ""
}
@Serializable
data class CateringTransaction(
    val date: String = "",
    val items: List<CateringItem> = emptyList(),
    val balance: JsonElement? = null
) {
    fun balanceText(): String = balance?.toString()?.trim('"') ?: ""
}
@Serializable
data class CateringData(val balance: JsonElement? = null, val transactions: List<CateringTransaction> = emptyList()) {
    fun balanceText(): String = balance?.toString()?.trim('"') ?: ""
}

@Serializable
data class SchoolDocument(
    val id: String = "",
    val summary: String? = null,
    val type: String? = null,
    val status: String? = null,
    val last_updated: String? = null
)
@Serializable
data class DocumentsData(val documents: List<SchoolDocument> = emptyList())

@Serializable
data class ExternalLink(val name: String = "", val url: String = "", val icon: String? = null)
@Serializable
data class ExternalLinksData(val links: List<ExternalLink> = emptyList())

@Serializable
data class MedicalNote(val type: String = "", val summary: String = "")
@Serializable
data class MedicalInfo(
    val have_emergency_consent: Boolean = false,
    val dietary_needs: List<String> = emptyList(),
    val medical_notes: List<MedicalNote> = emptyList()
)
@Serializable
data class MedicalData(val medical: MedicalInfo = MedicalInfo())

@Serializable
data class Teacher(
    val title: String = "",
    val forename: String = "",
    val surname: String = "",
    val email: String? = null,
    val phone: String? = null
) {
    fun fullName(): String = listOf(title, "$forename $surname".trim()).filter { it.isNotBlank() }.joinToString(" ")
}
@Serializable
data class TeachersData(val teachers: List<Teacher> = emptyList())

// ----- Extras wave 2: forms, absences, detentions, moments, assessment, report card -----
@Serializable
data class FormEntry(val id: Int = 0, val subject: String = "", val created: String = "", val due: String = "", val submitted: String? = null)
@Serializable
data class FormsData(val forms: List<FormEntry> = emptyList())
@Serializable
data class FormField(val id: Int = 0, val type: String = "", val required: Boolean = false, val content: JsonElement? = null)
@Serializable
data class FormBody(val id: Int = 0, val subject: String = "", val created: String = "", val due: String = "", val active: Boolean = true, val recipient_type: String = "", val fields: List<FormField> = emptyList())
@Serializable
data class FormDetailData(val form: FormBody = FormBody())
@Serializable
data class AbsenceMarks(val am: String = "", val pm: String = "")
@Serializable
data class AbsenceLog(val time: String = "", val message: String = "", val employee_id: String = "")
@Serializable
data class AbsenceEntry(val learner_id: String = "", val marks: AbsenceMarks = AbsenceMarks(), val log: List<AbsenceLog> = emptyList())
@Serializable
data class AbsencesData(val absences: List<AbsenceEntry> = emptyList())
@Serializable
data class DetentionSessionEntry(val id: Int = 0, val date: String = "", val description: String = "", val location: String = "", val start_time: String = "", val end_time: String = "", val num_of_detainees: Int? = null)
@Serializable
data class DetentionsData(val sessions: List<DetentionSessionEntry> = emptyList())
@Serializable
data class DetentionStudent(val id: String = "", val attended: String = "", val incident_date: String = "", val incident_type: String = "", val staff_comments: String = "")
@Serializable
data class DetentionSessionBody(val id: Int = 0, val date: String = "", val description: String = "", val location: String = "", val start_time: String = "", val end_time: String = "", val students: List<DetentionStudent> = emptyList())
@Serializable
data class DetentionDetailData(val session: DetentionSessionBody = DetentionSessionBody())
@Serializable
data class MomentAttachment(val filename: String = "", val mime_type: String = "", val url: String = "")
@Serializable
data class Moment(val id: String = "", val text: String = "", val created: String = "", val attachments: List<MomentAttachment> = emptyList())
@Serializable
data class MomentsData(val moments: List<Moment> = emptyList())
@Serializable
data class AssessmentCell(val name: String = "", val content: JsonElement? = null, val css: String = "", val visible: Boolean = true)
@Serializable
data class AssessmentGrid(val name: String = "", val applies_this_year: Boolean = false, val header: String = "", val footer: String = "", val resultstable: List<List<AssessmentCell>> = emptyList())
@Serializable
data class AssessmentData(val assessment: List<AssessmentGrid> = emptyList())
@Serializable
data class ReportCardTarget(val achieved: Boolean = false)
@Serializable
data class ReportCardClass(val comments: String = "", val initials: String = "", val label: String = "", val targets: List<ReportCardTarget> = emptyList())
@Serializable
data class ReportCardDay(val date: String = "", val classes: List<ReportCardClass> = emptyList())
@Serializable
data class ReportCardBody(val start_date: String = "", val end_date: String = "", val comments: String = "", val active: Boolean = true, val current: Boolean = false, val days: List<ReportCardDay> = emptyList())
@Serializable
data class ReportCardData(val card: ReportCardBody = ReportCardBody())
