# v0.7.8 silent re-login

Requested by Kacper on WhatsApp 2026-11-26 08:24 GMT: Edulink Two does not
autofill and sign-ins go stale; sign in automatically with the credentials
from the first sign-in.

## Change
- When a refresh or detail fetch reports an expired session, the app now signs
  in again silently with the Keystore-sealed remembered credentials (v0.7.7
  store) and retries once. No login screen if that works.
- `AutoReloginPolicy`: one attempt per 2 minutes. After a rejected-credentials
  failure (anything not network-like) it stops until he signs in by hand, so a
  changed password cannot lock the account by repeated tries.
- If there is no remembered sign-in, or it fails, behaviour is as before: login
  screen with prefilled fields.
- Needs "Remember me" checked on the last manual sign-in (default checked since
  0.7.7). He must sign in by hand once on 0.7.8 only if no sign-in is saved.
- Read-only toward the school API; the only call is the normal login.

## Verified
- local-tests/AutoReloginTest.kt (kotlinc JVM): PASS.
- Not verified: behaviour on a real device against the school API; the
  session timeout path itself. Needs Kacper's QA.
