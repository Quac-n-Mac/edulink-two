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
    val attachments: List<Attachment> = emptyList()
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
    val end_time: String? = null
) {
    val teachers: JsonElement? = null
    fun teacherName(): String = teachers?.toString()?.trim('"') ?: ""
    fun subjectName(): String = subject ?: teaching_group?.subject ?: ""
}

@Serializable
data class Day(val date: String, val name: String? = null, val lessons: List<Lesson> = emptyList())
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
    val subject: String = "",
    val body: String = "",
    val date: String = "",
    val read: String? = null,
    val sender: MessageSender? = null
)
@Serializable
data class MessagesData(val messages: List<Message> = emptyList())
