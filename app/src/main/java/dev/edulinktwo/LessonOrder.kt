package dev.edulinktwo

/** Numeric time ordering accepts 9:05 and 09:05. Missing/invalid times go last.
 * sortedBy is stable, so simultaneous lessons retain their incoming order.
 * This only changes display order, never the cached API response.
 */
internal fun lessonStartMinute(time: String?): Int {
    val parts = time?.trim()?.split(":") ?: return Int.MAX_VALUE
    if (parts.size !in 2..3) return Int.MAX_VALUE
    val hour = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
    val minute = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
    if (hour !in 0..23 || minute !in 0..59) return Int.MAX_VALUE
    if (parts.size == 3 && parts[2].toIntOrNull() !in 0..59) return Int.MAX_VALUE
    return hour * 60 + minute
}

internal fun <T> lessonsChronologically(lessons: List<T>, startTime: (T) -> String?): List<T> =
    lessons.sortedBy { lessonStartMinute(startTime(it)) }
