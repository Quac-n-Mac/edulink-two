package dev.edulinktwo

/** JNI bridge into the Rust core. All calls are JSON in / JSON out. */
object RustBridge {
    init {
        System.loadLibrary("edulink_bridge")
    }

    external fun nativeResolveSchool(code: String): String

    external fun nativeLogin(
        stateDir: String,
        cacheDir: String,
        host: String,
        schoolCode: String,
        schoolId: String,
        username: String,
        password: String,
        learnerId: String
    ): String

    external fun nativeRefresh(stateDir: String, cacheDir: String): String
    external fun nativeDemoLogin(stateDir: String, cacheDir: String): String
    external fun nativeGetCached(stateDir: String, cacheDir: String, name: String): String
    external fun nativeHwDetail(stateDir: String, cacheDir: String, id: String, source: String): String
    external fun nativeHwAttachment(stateDir: String, cacheDir: String, homeworkId: String, id: String): String
    external fun nativeNoticeFile(stateDir: String, cacheDir: String, id: String): String
    external fun nativeAccounts(stateDir: String): String
    external fun nativeSwitchAccount(stateDir: String, key: String): String
    external fun nativeRemoveAccount(stateDir: String, key: String): String
    external fun nativeFetchRaw(
        stateDir: String,
        cacheDir: String,
        name: String,
        method: String,
        paramsJson: String
    ): String
    external fun nativeSetDegraded(degraded: Boolean)
}
