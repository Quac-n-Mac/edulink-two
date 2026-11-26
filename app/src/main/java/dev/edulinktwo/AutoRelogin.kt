package dev.edulinktwo

/**
 * Policy for silent re-login with the remembered sign-in. Keeps it from
 * hammering the school server or locking the account: at most one attempt per
 * cooldown, and after a rejected-credentials failure it stops until the user
 * signs in by hand (a manual success calls [reset]).
 */
internal class AutoReloginPolicy(private val cooldownMs: Long = 120_000L) {
    private var lastAttemptAt = Long.MIN_VALUE
    private var blocked = false

    @Synchronized fun allow(now: Long): Boolean {
        if (blocked) return false
        if (lastAttemptAt != Long.MIN_VALUE && now - lastAttemptAt < cooldownMs) return false
        lastAttemptAt = now
        return true
    }

    @Synchronized fun failed(error: String?) { if (!looksTransient(error)) blocked = true }
    @Synchronized fun reset() { blocked = false; lastAttemptAt = Long.MIN_VALUE }

    companion object {
        fun looksTransient(error: String?): Boolean {
            val t = (error ?: "").lowercase()
            return "timeout" in t || "timed out" in t || "connect" in t || "network" in t ||
                "dns" in t || "offline" in t || "bridge failed" in t || "no response" in t || "unreachable" in t
        }
    }
}
