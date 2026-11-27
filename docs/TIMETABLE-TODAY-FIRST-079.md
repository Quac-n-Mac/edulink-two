# Timetable: today always on top (v0.7.9)

Requested by Kacper 2026-11-27 08:20 GMT (WhatsApp, in the authenticated chat): "can we like not show previous days so that TOday aka the present day always appears on top? Rn I gotta scroll and find it" - clarified "In the timetable".

Change: the timetable tab now shows only today and upcoming days. Past days are hidden, so the current day is the first header without scrolling. On a weekend the next school day leads. If the cached timetable holds no today-or-later day (stale cache), the tab shows "No upcoming days cached. Pull to refresh." instead of a blank list. Display filter only; the cached API response is untouched.

Changed files:
- app/src/main/java/dev/edulinktwo/TimetableFilter.kt (new pure function `upcomingByDate`)
- app/src/main/java/dev/edulinktwo/ui/HomeScreen.kt (TimetableTab uses the filtered list, empty fallback added)
- local-tests/TimetableFilterTest.kt (standalone JVM regression test)

Verified: compiled the actual helper with Kotlin 2.1.0, ran its JVM test successfully. Cases: past days dropped, today first, mid-week, weekend rolls to next school day, stale cache empty, incoming order kept, source list not mutated, empty input.

Not verified: full Android build, Compose rendering, real device. versionCode 42 / versionName 0.7.9. CI builds and publishes the release; check the APK before calling it done.

Local test:
kotlinc app/src/main/java/dev/edulinktwo/TimetableFilter.kt local-tests/TimetableFilterTest.kt -include-runtime -d /tmp/timetable-filter-test.jar
java -jar /tmp/timetable-filter-test.jar
