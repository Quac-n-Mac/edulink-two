# Timetable display-order fix, local only

Prepared 2026-11-22. Authenticated WhatsApp confirmation recovered at 21:43:13-15 GMT. Timetable sort patch submitted for v0.7.3 nightly; Android build result pending.

Change: the Compose timetable uses a copied list sorted by numeric start time ascending. Non-zero-padded hours work; null/invalid times go last. Equal times retain incoming order. No API, cache, account or attendance writes.

Changed files:
- app/src/main/java/dev/edulinktwo/LessonOrder.kt (new pure function)
- app/src/main/java/dev/edulinktwo/ui/HomeScreen.kt (use ordered list)
- local-tests/LessonOrderTest.kt (standalone JVM regression test)

Verified: compiled the actual Kotlin helper with Kotlin 2.1.0, ran its JVM test successfully. Cases: ascending order, 9:15 versus 10:15, seconds, stable ties, null/invalid times, empty input and unchanged input list.

Not verified: full Android build, Compose rendering, real device screenshot. App versionCode bumped to 36 / versionName 0.7.3. Existing CI will compile the Android release and publish its nightly if green. Inspect CI and timetable pixels before claiming Android completion.

Local test:
kotlinc app/src/main/java/dev/edulinktwo/LessonOrder.kt local-tests/LessonOrderTest.kt -include-runtime -d /tmp/lesson-order-test.jar
java -jar /tmp/lesson-order-test.jar
