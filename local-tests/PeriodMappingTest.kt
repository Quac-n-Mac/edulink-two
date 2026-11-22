package dev.edulinktwo
fun main() {
    val day = json.decodeFromString<Day>("""{"date":"2026-11-23","periods":[{"id":2,"start_time":"10:15","end_time":"11:15"},{"name":"1","start_time":"9:15","end_time":"10:15"}],"lessons":[{"period_id":2,"subject":"Second","teachers":"Teacher Two"},{"period_id":"1","subject":"First","teachers":"Teacher One"}]}""")
    val sorted = day.orderedLessons()
    check(sorted.map { it.subject } == listOf("First", "Second"))
    check(sorted.map { it.start_time } == listOf("9:15", "10:15"))
    check(sorted.first().teacherName() == "Teacher One")
    check(day.lessons.first().subject == "Second")
    println("PASS: period IDs/names, numeric/string IDs, inherited times, retained teachers and immutable source")
}
