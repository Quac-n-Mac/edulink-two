package dev.edulinktwo

/** Detect authentication failures without treating offline/permission errors as expiry. */
internal fun sessionNeedsSignIn(messages: List<String>): Boolean = messages.any {
    val text = it.lowercase()
    "you are not logged in" in text || "session expired" in text ||
        "invalid token" in text || "token expired" in text || "unauthorized (401)" in text
}
