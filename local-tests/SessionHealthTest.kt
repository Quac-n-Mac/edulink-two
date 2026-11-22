package dev.edulinktwo
fun main() {
    check(sessionNeedsSignIn(listOf("homework: api error: You are not logged in")))
    check(sessionNeedsSignIn(listOf("session expired (401) - log in again")))
    check(!sessionNeedsSignIn(listOf("http send timeout", "access denied for this class")))
    check(!sessionNeedsSignIn(emptyList()))
    println("PASS: session expiry distinguished from network and permission errors")
}
