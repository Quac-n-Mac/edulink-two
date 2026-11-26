package dev.edulinktwo
fun main() {
    val p = AutoReloginPolicy(cooldownMs = 1000)
    check(p.allow(0)); check(!p.allow(500)); check(p.allow(1500))
    p.failed("http send timeout"); check(p.allow(3000))   // transient: keeps trying later
    p.failed("Invalid username or password"); check(!p.allow(10_000)) // rejected: stop
    p.reset(); check(p.allow(20_000))
    println("PASS: auto re-login cooldown and stop-on-rejected-credentials")
}
