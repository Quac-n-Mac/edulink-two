package dev.edulinktwo

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class Tab { HOMEWORK, TIMETABLE, POINTS, MORE, PROFILE }

data class UiState(
    val loggedIn: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val currentAccount: String? = null,
    val tab: Tab = Tab.HOMEWORK,
    val homework: HomeworkData? = null,
    val timetable: TimetableData? = null,
    val achievement: AchievementData? = null,
    val behaviour: BehaviourData? = null,
    val attendance: AttendanceData? = null,
    val exams: ExamsData? = null,
    val notices: NoticeboardData? = null,
    val calendar: CalendarData? = null,
    val clubs: ClubsData? = null,
    val personal: PersonalData? = null,
    val inbox: MessagesData? = null,
    val lastUpdated: String? = null,
    val refreshing: Boolean = false,
    val degraded: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val showLogin: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val stateDir = app.filesDir.absolutePath
    private val cacheDir = app.cacheDir.absolutePath

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        // Cache-first: render the last-known snapshot on the first frame,
        // then refresh behind it. Never block the UI on the network.
        loadCachedAll()
        reloadAccounts()
        watchConnectivity()
        if (_ui.value.loggedIn) refresh()
    }

    private fun loadCachedAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val hw = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "homework") }.getOrNull()
            val tb = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "timetable") }.getOrNull()
            val ach = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "achievement") }.getOrNull()
            val beh = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "behaviour") }.getOrNull()
            val att = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "attendance") }.getOrNull()
            val ex = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "exams") }.getOrNull()
            val nb = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "noticeboard") }.getOrNull()
            val cal = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "calendar") }.getOrNull()
            val cl = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "clubs") }.getOrNull()
            val per = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "personal") }.getOrNull()
            val inbox = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "inbox") }.getOrNull()
            val (hwData, hwAt) = decodeSurface<HomeworkData>(hw)
            val (tbData, _) = decodeSurface<TimetableData>(tb)
            val (achData, _) = decodeSurface<AchievementData>(ach)
            val (behData, _) = decodeSurface<BehaviourData>(beh)
            _ui.value = _ui.value.copy(
                homework = hwData, timetable = tbData,
                achievement = achData, behaviour = behData,
                attendance = decodeSurface<AttendanceData>(att).first,
                exams = decodeSurface<ExamsData>(ex).first,
                notices = decodeSurface<NoticeboardData>(nb).first,
                calendar = decodeSurface<CalendarData>(cal).first,
                clubs = decodeSurface<ClubsData>(cl).first,
                personal = decodeSurface<PersonalData>(per).first,
                inbox = decodeSurface<MessagesData>(inbox).first,
                lastUpdated = hwAt,
                error = lastDecodeError ?: _ui.value.error
            )
        }
    }

    fun reloadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeAccounts(stateDir) }.getOrNull() ?: return@launch
            val res = runCatching { json.decodeFromString<AccountsResult>(raw) }.getOrNull() ?: return@launch
            _ui.value = _ui.value.copy(
                loggedIn = res.current != null,
                accounts = res.accounts,
                currentAccount = res.current
            )
        }
    }

    /** Pre-login: resolve a school code/postcode like the stock app. */
    fun resolveSchool(code: String, onDone: (Boolean, SchoolResolveResult?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeResolveSchool(code.trim()) }
                .getOrElse { "{\"ok\":false,\"error\":\"bridge failed\"}" }
            val res = runCatching { json.decodeFromString<SchoolResolveResult>(raw) }.getOrNull()
            if (res?.ok == true) {
                res.logo_b64?.let { b64 ->
                    runCatching {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        java.io.File(stateDir, "school-${res.school_id}.png").writeBytes(bytes)
                    }
                }
                withContext(Dispatchers.Main) { onDone(true, res, null) }
            } else {
                withContext(Dispatchers.Main) { onDone(false, null, res?.error ?: "school not found") }
            }
        }
    }

    /** Saved profile photo for an account key, if we have one. */
    fun avatarFile(key: String?): java.io.File? {
        if (key.isNullOrEmpty()) return null
        val f = java.io.File(stateDir, "avatar-$key.jpg")
        return if (f.exists()) f else null
    }

    /** Saved crest for a school id, if we have one. */
    fun crestFile(schoolId: Long): java.io.File? {
        if (schoolId <= 0) return null
        val f = java.io.File(stateDir, "school-$schoolId.png")
        return if (f.exists()) f else null
    }

    fun login(host: String, schoolCode: String, schoolId: Long, username: String, password: String, learnerId: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching {
                RustBridge.nativeLogin(stateDir, cacheDir, host, schoolCode, schoolId.toString(), username, password, learnerId)
            }.getOrElse { "{\"ok\":false,\"error\":\"bridge failed\"}" }
            val res = runCatching { json.decodeFromString<ApiResult>(raw) }.getOrNull()
            if (res?.ok == true) {
                res.avatar_b64?.let { b64 ->
                    runCatching {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        java.io.File(stateDir, "avatar-${res.key}.jpg").writeBytes(bytes)
                    }
                }
                reloadAccounts()
                loadCachedAll()
                _ui.value = _ui.value.copy(showLogin = false)
                withContext(Dispatchers.Main) { onDone(true, null) }
            } else {
                withContext(Dispatchers.Main) { onDone(false, res?.error ?: "login failed") }
            }
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return // coalesce refresh requests
        _ui.value = _ui.value.copy(refreshing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeRefresh(stateDir, cacheDir) }.getOrNull()
            val res = raw?.let { runCatching { json.decodeFromString<ApiResult>(it) }.getOrNull() }
            loadCachedAll()
            val failedList = res?.failed?.takeIf { it.isNotEmpty() }
            _ui.value = _ui.value.copy(
                refreshing = false,
                error = when {
                    res?.ok == false -> res.error
                    failedList != null -> "Some surfaces failed: " + failedList.joinToString("; ")
                    else -> null
                }
            )
        }
    }

    fun switchAccount(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RustBridge.nativeSwitchAccount(stateDir, key) }
            _ui.value = _ui.value.copy(showLogin = false)
            reloadAccounts()
            // Instant: cached surfaces for this account render immediately.
            _ui.value = _ui.value.copy(homework = null, timetable = null, achievement = null, behaviour = null, lastUpdated = null)
            loadCachedAll()
            refresh()
        }
    }

    fun removeAccount(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RustBridge.nativeRemoveAccount(stateDir, key) }
            reloadAccounts()
            _ui.value = _ui.value.copy(homework = null, timetable = null, achievement = null, behaviour = null)
            loadCachedAll()
        }
    }

    fun selectTab(tab: Tab) { _ui.value = _ui.value.copy(tab = tab) }

    fun showAddAccount() { _ui.value = _ui.value.copy(showLogin = true) }

    /** Download a homework attachment to the files dir; returns a content URI to open. */
    fun openAttachment(id: String, onDone: (Boolean, String?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeHwAttachment(stateDir, cacheDir, id) }.getOrNull()
            val res = raw?.let { runCatching { json.decodeFromString<AttachmentResult>(it) }.getOrNull() }
            if (res?.ok == true && res.data_b64 != null) {
                val result = runCatching {
                    val bytes = android.util.Base64.decode(res.data_b64, android.util.Base64.DEFAULT)
                    val safe = (res.filename ?: "attachment").replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val dir = java.io.File(cacheDir, "attachments").apply { mkdirs() }
                    val f = java.io.File(dir, safe)
                    f.writeBytes(bytes)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        getApplication(), "dev.edulinktwo.fileprovider", f
                    )
                    Pair(uri.toString(), res.mime_type)
                }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) onDone(true, result.getOrNull()!!.first + "|" + (result.getOrNull()!!.second ?: ""), null)
                    else onDone(false, null, "could not save attachment")
                }
            } else {
                withContext(Dispatchers.Main) { onDone(false, null, res?.error ?: "attachment fetch failed") }
            }
        }
    }

    fun homeworkDetail(id: String, onDone: (HomeworkDetail?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeHwDetail(stateDir, cacheDir, id) }.getOrNull()
            val (d, at) = decodeSurface<HomeworkDetail>(raw)
            withContext(Dispatchers.Main) { onDone(d, at) }
        }
    }

    private fun watchConnectivity() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val down = caps.linkDownstreamBandwidthKbps
                val poor = down in 1..1499
                val offline = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val cur = _ui.value
                if (cur.degraded == poor && cur.offline == offline) return // no churn during scrolling
                RustBridge.nativeSetDegraded(poor)
                _ui.value = cur.copy(degraded = poor, offline = offline)
                if (!offline && cur.offline) refresh() // back online: refresh once
            }
            override fun onLost(network: Network) {
                RustBridge.nativeSetDegraded(true)
                _ui.value = _ui.value.copy(offline = true)
            }
            override fun onAvailable(network: Network) {
                if (_ui.value.offline) _ui.value = _ui.value.copy(offline = false)
            }
        })
    }
}

fun ageLabel(iso: String?): String {
    if (iso == null) return "never"
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val secs = (Instant.now().epochSecond - then.epochSecond).coerceAtLeast(0)
    return when {
        secs < 90 -> "${secs}s ago"
        secs < 5400 -> "${secs / 60}m ago"
        secs < 172800 -> "${secs / 3600}h ago"
        else -> "${secs / 86400}d ago"
    }
}
