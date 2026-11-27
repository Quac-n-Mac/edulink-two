package dev.edulinktwo

data class TDay(val date: String, val label: String)

fun main() {
    val week = listOf(
        TDay("2026-11-23", "Mon"),
        TDay("2026-11-24", "Tue"),
        TDay("2026-11-25", "Wed"),
        TDay("2026-11-26", "Thu"),
        TDay("2026-11-27", "Fri"),
        TDay("2026-11-30", "next Mon"),
        TDay("2026-12-01", "next Tue"),
    )
    // Today on top: only today and future days survive, past days dropped.
    check(upcomingByDate(week, "2026-11-27") { it.date }.map { it.label } == listOf("Fri", "next Mon", "next Tue"))
    // Mid-week: earlier same-week days go, today leads.
    check(upcomingByDate(week, "2026-11-25") { it.date }.map { it.label } == listOf("Wed", "Thu", "Fri", "next Mon", "next Tue"))
    // Weekend: next school day leads the list.
    check(upcomingByDate(week, "2026-11-28") { it.date }.map { it.label } == listOf("next Mon", "next Tue"))
    // All past (stale cache): empty, so the UI shows its pull-to-refresh fallback.
    check(upcomingByDate(week, "2027-01-05") { it.date }.isEmpty())
    // Everything upcoming: unchanged.
    check(upcomingByDate(week, "2026-11-01") { it.date }.size == 7)
    // Incoming order is kept (ISO dates compare correctly as text, but order comes from the API).
    val unordered = listOf(TDay("2026-12-01", "b"), TDay("2026-11-27", "a"))
    check(upcomingByDate(unordered, "2026-11-27") { it.date }.map { it.label } == listOf("b", "a"))
    // Source list is not mutated.
    check(week.size == 7 && week.first().label == "Mon")
    // Empty input.
    check(upcomingByDate(emptyList<TDay>(), "2026-11-27") { it.date }.isEmpty())
    println("PASS: past days dropped, today on top, weekend rolls to next school day, stale cache empty, order kept, source immutable, empty input")
}
