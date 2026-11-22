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
    val id: JsonObject? = null,
    val title: String = "",
    val forename: String = "",
    val surname: String = ""
) {
    fun display(): String = listOf(title, "$forename $surname".trim()).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class Attachment(val kind: String? = null, val filename: String? = null)

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
data class Account(val key: String, val username: String, val display_name: String, val school: String, val host: String)
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
    val prefetch_ms: Long? = null
)

/** Cached envelope written by the Rust core. `data` stays raw until decoded per surface. */
@Serializable
data class Cached(val fetched_at: String, val data: JsonObject)

fun decodeCached(raw: String): Cached? = runCatching {
    json.decodeFromString<Cached>(raw)
}.getOrNull()

inline fun <reified T> decodeSurface(raw: String?): Pair<T?, String?> {
    if (raw == null) return null to null
    val cached = decodeCached(raw) ?: return null to null
    return runCatching { json.decodeFromJsonElement<T>(cached.data) }.getOrNull() to cached.fetched_at
}
