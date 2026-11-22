# v0.7.6 homework attachment fix

Live stock app inspection on 2026-11-22 confirmed the affected homework has one PDF in the LIST item's attachments. The stock UI renders that list metadata, while HomeworkDetails provides the description. Our detail sheet only used the detail response and lost list-only file metadata.

Fix: merge list and detail attachments without duplicates, retain homework ID, display filenames below text. Download now sends BOTH homework_id and attachment_id, as the stock app does. Previously it sent only attachment_id. No school writes or marks-as-complete actions. Failed external viewer launches show a useful error instead of silently disappearing.

Verified: actual Kotlin model serialization and list-only merge regression passed, Rust request-parameter regression passed, Rust workspace tests green. Android CI pending. No actual APK attachment-open pixels yet. Live stock source used for parameter comparison: https://www.edulinkone.com/js/index.bundle.js?58dd7772a67548294aa0 . Live stock homework page: https://www.edulinkone.com/#!/homework/list . No pupil data or tokens copied into this status file.
