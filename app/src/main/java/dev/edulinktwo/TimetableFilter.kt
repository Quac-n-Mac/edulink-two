package dev.edulinktwo

/** Days shown in the timetable tab: today and upcoming days only, in the order
 * the API returned them. Past days are dropped so today is always on top
 * without scrolling. Dates are ISO yyyy-MM-dd, so text compare is a date
 * compare. This only filters what is displayed, never the cached response.
 */
internal fun <T> upcomingByDate(items: List<T>, today: String, dateOf: (T) -> String): List<T> =
    items.filter { dateOf(it) >= today }
