# Edulink Two

A fast, offline-first, **read-only** Edulink One client. Stock look (original
Edulink assets, Edulink blue), new number.

> **Private repo, personal use only.** The launcher icon and header art are the
> vendor's original assets pulled from the official APK, so this can never be
> published.

## Stack

- **Rust core** (`src/` + `bridge/`): all networking (JSON-RPC 2.0), models,
  session/account store, and the local cache. The Android app is a thin shell
  over it via JNI (`bridge/`, one JSON string in / one out).
- **Android shell** (`app/`): Kotlin + Jetpack Compose, arm64-v8a only,
  R8 + resource shrinking, stock Edulink blue theme (#00365D) and icon.
- **CLI/TUI** (same core, desktop dev tool): `cargo run -- hw|tb|points|tui`.

## Speed design

- **Instant start**: the first frame renders the last-known cache snapshot.
  No splash work, no network on the UI thread.
- **Pre-cache on login**: homework, timetable, achievement and behaviour are
  fetched **in parallel** right after login, so every section opens instantly.
- **Cache-first rendering**: every screen renders the cache and refreshes
  behind it. Stale data always beats a spinner.
- **Snappy UI**: all transitions run at 120ms (~60% faster than stock). Tab
  switches in the TUI are instant.
- **No spinner culture**: a single "refreshing..." indicator, data stays on
  screen while it refreshes.

## Low-connectivity mode (school-bus mode)

- Offline-first: the last-known snapshot is always viewable, even with zero
  connectivity. TTLs are hints, not gates - stale shows with its age.
- Poor connectivity (detected via Android network capabilities): tight
  per-request timeouts (8s), refresh requests coalesce, one refresh in flight.
- Reconnect triggers exactly one refresh.
- Attachments are **listed, never auto-downloaded** - no big media on demand.

## Multi-account

Parity with stock Edulink: every login is saved (`accounts.json`, 0600) with
its own token and its own cache namespace; switching is a tap (app) or
`edulink switch <username>` (CLI).

## Read-only guarantee

The only wire methods implemented anywhere in this codebase are:

`EduLink.Login`, `EduLink.LoginContext`, `EduLink.Status`, `EduLink.Homework`,
`EduLink.HomeworkDetails`, `EduLink.Timetable`, `EduLink.Achievement`,
`EduLink.Behaviour`

All reads. There is no write-method code path to audit. The full 195-method
map lives in the rea-lab repo (`reports/edulink-methods.md`); the remaining
~187 slot in as per-area modules on the same Transport.

## CLI

```
edulink login <username> [--host www9.edulinkone.com] [--learner-id N]
edulink accounts | switch <username> | logout [username]
edulink hw            # homework (cache-first)
edulink hw+ <id>      # expand one homework's description
edulink tb [date]     # timetable
edulink points        # achievement + behaviour + detentions
edulink status        # current/next lesson
edulink sync          # parallel refresh of all surfaces
edulink tui           # full-screen terminal UI
edulink --mock fixtures hw   # fixture mode, no network
edulink --offline hw         # last-known data only, zero network
edulink --poor hw            # degraded-mode timeouts
edulink --json hw            # raw JSON
```

## Build

```
cargo build --release           # CLI/TUI
cargo test                      # fixture tests, no network
```

APK: GitHub Actions (`android.yml`) builds the Rust core for arm64-v8a with
cargo-ndk, then the APK with Gradle, and publishes it to Releases.
Set `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD` secrets for a properly signed release APK; without
them CI ships a debug-signed APK (fine for personal installs).

## Roadmap

v1: login + homework + timetable + points (done in core + shell).
Next: communicator read views, attendance, exams, notices, calendar -
each is a new module on `Transport` plus a Compose tab. Write methods are
out of scope permanently.
