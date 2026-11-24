# v0.7.7 remembered sign-in

Requested by the user 2026-11-24 08:34 GMT: the app forgot his sign-in every
open. Stock Edulink One shows a saved-account picker and, after picking,
prefills username AND password behind a checked "Remember me" box, so sign-in
is one tap. He asked for the same flow, keeping Edulink Two's parallax/theme
styling. Flow copied; styling unchanged.

## Change

- New `CredentialStore.kt`: versioned, dependency-free codec plus a store
  backed by a sealed blob file (`credentials.enc`, app-private files dir).
- New `KeystoreBox.kt`: AES-256/GCM sealing; the key is generated inside the
  Android Keystore (alias `edulinktwo.credentials.v1`) and never leaves it.
  Plaintext credentials are never written to disk, the repo, or logs.
- `AppViewModel.login(...)` takes `remember: Boolean`; on a real server-side
  login success it saves (or, when unchecked, deletes) the remembered
  sign-in for that account key. `removeAccount` also forgets it.
  New `savedCredential(key)` reads one back for prefill.
- `LoginScreen`: "REMEMBER ME?" now defaults to checked (stock parity, and
  it was previously decorative - it never reached the login call). On the
  expired-session path the username and password fields prefill from the
  remembered sign-in, so recovery is: open app, tap LOG IN. Parallax,
  glass, palette and picker UI unchanged.
- No Rust changes; no school API changes; still read-only toward the API.
  Troseddeg and Bomboclad untouched.

## Failure behavior

Undecryptable or corrupt blob (keystore key wiped, OS upgrade edge) -> the
store starts empty and deletes the blob; the app falls back to normal manual
entry. Decoding never throws.

## Verified

- `local-tests/CredentialStoreTest.kt` (14 cases, kotlinc 2.1.0 JVM):
  round-trip with tabs/newlines/unicode in passwords, multi-account
  independence, reload from disk, overwrite, remove, corrupt/unknown-version
  inputs failing closed to empty, undecryptable blob deleted, and no
  plaintext username present in the sealed file bytes. ALL TESTS PASSED.
- Not verified: Android Keystore behavior on a real device, Compose prefill
  pixels, actual login against the school API. Those need the user's device
  QA (his build rule: he QA's every build).

## Files

- app/src/main/java/dev/edulinktwo/CredentialStore.kt (new)
- app/src/main/java/dev/edulinktwo/KeystoreBox.kt (new)
- app/src/main/java/dev/edulinktwo/AppViewModel.kt
- app/src/main/java/dev/edulinktwo/ui/LoginScreen.kt
- local-tests/CredentialStoreTest.kt (new)
- app/build.gradle.kts: versionCode 40, versionName 0.7.7
