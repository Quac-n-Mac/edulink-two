# v0.7.4 session and timetable recovery

Reported 2026-11-22 via authenticated WhatsApp. Screenshots showed an expired server session, cached data five hours old, missing timetable times and a class-detail unavailable message.

Changes:
- Recognize server session-expiry text in partial refresh failures, preserve cached data and route to sign-in. Reuse the saved school details so no school-code re-entry is required. Passwords are not stored, so actual token renewal still requires signing in.
- Fix initial refresh race: refresh begins after saved accounts load, not before the asynchronous account read finishes.
- Do not suppress partial refresh failures or convert a missing bridge response into apparent success.
- Resolve missing lesson times from day.periods by matching lesson.period_id to period.id or period.name. Numeric/string IDs both work. Rust retains period.id in new cached responses; old cached responses may require refresh to regain lost IDs.
- Keep teacher data when copying lessons for display ordering.
- Recompute seating/register matches when refreshed lists change. Explain absent cached data without claiming every student has access to a seating plan. No fabricated plans or cross-class fallback.
- Preserve custom homework entries. No school API writes, password storage or destructive cache clearing added.

Verified locally:
- Kotlin helper sort regression test passed.
- Kotlin serialization test compiled actual Models.kt and mapped mixed numeric/string period IDs to start times, sorted correctly, preserved teachers and original input.
- Kotlin session-health test distinguishes auth expiry from timeouts/permission errors.
- Rust core compiles with retained period ID; full test output recorded separately.

Pending:
- Android CI build and nightly asset confirmation.
- Real device/Android screenshot check. No claim of successful real-account reauthentication; it needs the user's credentials on their device.
- Old 0.7.2 CI status was failed only because its screenshot emulator failed. Build and publish had succeeded; that status was not evidence of an APK compile failure.

## Later closeout

v0.7.5 additionally retained LoginContext authtoken and used it for prefetch/persistence. New token-rotation regression passed; core Rust tests5/5, Kotlin tests3/3 and Android compile passed. The6121413byte APK was downloaded/ZIP checked. User later installed it and showed recently updated homework. Exact cause of the original rejected on-device token was not proven. See HANDOFF.md and ATTACHMENTS-076.md for the newer shipped attachment fix.
