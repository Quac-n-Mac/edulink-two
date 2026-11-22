package dev.edulinktwo
fun main() {
    val item = json.decodeFromString<HomeworkItem>("""{"id":"326871","activity":"Fixture homework","attachments":[{"id":"161704","filename":"fixture.pdf"}]}""")
    val detail = json.decodeFromString<HomeworkDetail>("""{"homework":{"description":"Detail omits attachments"}}""")
    val merged = detail.withListAttachments(item)
    check(merged.homework.idString() == "326871")
    check(merged.homework.attachments.single().filename == "fixture.pdf")
    check(merged.withListAttachments(item).homework.attachments.size == 1)
    check(detail.homework.attachments.isEmpty())
    println("PASS: list-only attachment metadata retained under detail, homework ID retained, duplicates removed, source unchanged")
}
