# Handoff, 2026-11-22 22:53 GMT

## Latest shipped

- v0.7.6 / versionCode39, commit f5822e6. Android compile and publish succeeded in workflow45077053397. APK6121733bytes downloaded back, ZIP integrity clean and manifest present.
- Release: https://github.com/Quac-n-Mac/edulink-two/releases/tag/v0.7.6
- Direct APK: https://github.com/Quac-n-Mac/edulink-two/releases/download/v0.7.6/edulink-two.apk

## Fixes and evidence

- v0.7.5 fixed discarded LoginContext authtoken, initial asynchronous account-read refresh race, hidden partial failures, server-rejection recovery and period time mapping before sorting. Rust token-rotation regression plus core tests passed. Kotlin sort, serialization/period-map and session-health tests passed. User installed0.7.5 and sent a screenshot with recently updated homework, without the earlier login error. This does not prove every screen now works.
- v0.7.6 merges homework list attachments into detail display. Live stock account showed the affected Pizza homework PDF in list metadata; stock UI displays those attachments beside detail text. Download now passes both homework_id and attachment_id. Kotlin list-only merge and Rust request-parameter tests passed.
- No school API writes, no password storage added, no fake-homework deletion. Keep Troseddeg and Bomboclad.

## Pending

- User feedback after installing0.7.6: confirm PDF filename row appears below homework text and tapping opens a viewer. APK compile is verified; actual Android pixels/PDF viewer launch not yet observed.
- Optional CI emulator screenshot job only covers demo/login, not affected real-account flow. It has repeatedly booted slowly and is not proof of attachment behavior.
- Older status docs describe intermediate builds; this handoff and latest version take precedence.

## Publishing route

- Repository already exists; do not recreate or change visibility.
- Bump versionCode and versionName in app/build.gradle.kts for a new APK. Existing android-apk workflow builds on main push and publishes versioned prerelease plus nightly pointer on compile success. Its separate screenshot job can fail while compile/publish succeed, inspect jobs rather than only overall status.
- Push uses normal authenticated Git/GitHub access. Never embed an access token in a remote URL, source file, status note or chat. In this continuing workspace an existing local credential-helper route was used; successor should use approved account credentials/integration without printing values. No credentials are in this repo or this note.
- Avoid pushing tiny documentation-only changes while a valuable build is running; workflow concurrency cancels the older run.
- Before calling a build installable, verify compile/publish jobs and download the actual release asset, not merely a successful source push.

## Scope

The user asked on authenticated WhatsApp to fix the app. Prior email-only request was not used as publication authority; confirmation arrived21:43. Keep school data read-only. Do not infer that cached account identity proves a current server session. Do not label a specific user's token expired/rotated without evidence.
