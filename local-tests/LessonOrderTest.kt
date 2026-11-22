package dev.edulinktwo

fun main() {
    val incoming = listOf("14:35", "9:15", "11:35", "10:15", "13:35")
    check(lessonsChronologically(incoming) { it } == listOf("9:15", "10:15", "11:35", "13:35", "14:35"))
    check(incoming.first() == "14:35") // original cache not mutated
    val edge = listOf<String?>(null, "bad", "10:00", "09:00:30", "9:00", "24:00", "12:99", "")
    check(lessonsChronologically(edge) { it } == listOf("09:00:30", "9:00", "10:00", null, "bad", "24:00", "12:99", ""))
    check(lessonsChronologically(emptyList<String>()) { it }.isEmpty())
    check(lessonStartMinute("00:00") == 0)
    check(lessonStartMinute("23:59") == 1439)
    check(lessonStartMinute("09:00:70") == Int.MAX_VALUE)
    println("PASS: ascending order, non-padded times, seconds, stable ties, missing/invalid times, empty input and immutable source")
}
