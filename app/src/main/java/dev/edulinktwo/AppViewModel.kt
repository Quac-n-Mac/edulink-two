package dev.edulinktwo

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val outbox: MessagesData? = null,
    val registers: RegistersData? = null,
    val seatingPlans: SeatingPlansData? = null,
    val catering: CateringData? = null,
    val documents: DocumentsData? = null,
    val externalLinks: ExternalLinksData? = null,
    val medical: MedicalData? = null,
    val teachers: TeachersData? = null,
    val lastUpdated: String? = null,
    val refreshing: Boolean = false,
    val degraded: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val showLogin: Boolean = false,
    val sessionExpired: Boolean = false,
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
        reloadAccounts(refreshAfter = true)
        watchConnectivity()
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
            val outbox = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "outbox") }.getOrNull()
            val regs = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "registers") }.getOrNull()
            val plans = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "seatingplans") }.getOrNull()
            val cat = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "catering") }.getOrNull()
            val docs = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "documents") }.getOrNull()
            val links = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "externallinks") }.getOrNull()
            val med = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "medical") }.getOrNull()
            val tch = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "teachers") }.getOrNull()
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
                outbox = decodeSurface<MessagesData>(outbox).first,
                registers = decodeSurface<RegistersData>(regs).first,
                seatingPlans = decodeSurface<SeatingPlansData>(plans).first,
                catering = decodeSurface<CateringData>(cat).first,
                documents = decodeSurface<DocumentsData>(docs).first,
                externalLinks = decodeSurface<ExternalLinksData>(links).first,
                medical = decodeSurface<MedicalData>(med).first,
                teachers = decodeSurface<TeachersData>(tch).first,
                lastUpdated = hwAt,
                error = lastDecodeError ?: _ui.value.error
            )
        }
    }

    fun reloadAccounts(refreshAfter: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeAccounts(stateDir) }.getOrNull() ?: return@launch
            val res = runCatching { json.decodeFromString<AccountsResult>(raw) }.getOrNull() ?: return@launch
            _ui.value = _ui.value.copy(
                loggedIn = res.current != null,
                accounts = res.accounts,
                currentAccount = res.current
            )
            if (refreshAfter && res.current != null) refresh()
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
                recoverExpiredSession(res?.error)
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
                _ui.value = _ui.value.copy(showLogin = false, sessionExpired = false, error = null)
                withContext(Dispatchers.Main) { onDone(true, null) }
            } else {
                withContext(Dispatchers.Main) { onDone(false, res?.error ?: "login failed") }
            }
        }
    }

    /** Offline demo account: fixtures only, no network. */
    fun loginDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RustBridge.nativeDemoLogin(stateDir, cacheDir) }
            reloadAccounts()
            loadCachedAll()
            _ui.value = _ui.value.copy(showLogin = false)
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return // coalesce refresh requests
        _ui.value = _ui.value.copy(refreshing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeRefresh(stateDir, cacheDir) }.getOrNull()
            val res = raw?.let { runCatching { json.decodeFromString<ApiResult>(it) }.getOrNull() }
            loadCachedAll()
            val failedList = res?.failed.orEmpty()
            val expired = sessionNeedsSignIn(failedList + listOfNotNull(res?.error))
            _ui.value = _ui.value.copy(
                refreshing = false,
                sessionExpired = expired,
                showLogin = expired || _ui.value.showLogin,
                error = when {
                    expired -> "Your school session expired. Sign in again to refresh. Your saved data has been kept."
                    res == null -> "Refresh failed. Check your connection and try again."
                    failedList.isNotEmpty() -> "Some surfaces failed: " + failedList.joinToString("; ")
                    res.ok == false -> res.error ?: "Refresh failed. Saved data has been kept."
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
    fun openAttachment(homeworkId: String, id: String, onDone: (Boolean, String?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeHwAttachment(stateDir, cacheDir, homeworkId, id) }.getOrNull()
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
                recoverExpiredSession(res?.error)
                withContext(Dispatchers.Main) { onDone(false, null, res?.error ?: "attachment fetch failed") }
            }
        }
    }

    /** Download a noticeboard document; returns "uri|mime" like openAttachment. */
    fun openNoticeFile(id: String, onDone: (Boolean, String?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeNoticeFile(stateDir, cacheDir, id) }.getOrNull()
            val res = raw?.let { runCatching { json.decodeFromString<AttachmentResult>(it) }.getOrNull() }
            if (res?.ok == true && res.data_b64 != null) {
                val result = runCatching {
                    val bytes = android.util.Base64.decode(res.data_b64, android.util.Base64.DEFAULT)
                    val safe = (res.filename ?: "document").replace(Regex("[^A-Za-z0-9._-]"), "_")
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
                    else onDone(false, null, "could not save document")
                }
            } else {
                recoverExpiredSession(res?.error)
                withContext(Dispatchers.Main) { onDone(false, null, res?.error ?: "document fetch failed") }
            }
        }
    }

    /** Full class register for a register id (or the demo canned one). */
    fun registerDetail(id: String, onDone: (RegisterDetail?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching {
                RustBridge.nativeFetchRaw(stateDir, cacheDir, "register-$id", "EduLink.Register", "{\"register_id\":\"$id\",\"format\":2}")
            }.getOrNull()
            val (d, _) = decodeSurface<RegisterDetail>(raw)
            val err = if (d == null) {
                raw?.let { runCatching { json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() }
                    ?: lastDecodeError ?: "register fetch failed (no response)"
            } else null
            recoverExpiredSession(err)
            withContext(Dispatchers.Main) { onDone(d, err) }
        }
    }

    /** Seating plan layout for a timetable lesson. */
    fun seatingPlanDetail(entry: SeatingPlanEntry, onDone: (SeatingPlanDetail?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = (entry.group_name + "-" + entry.room_name).replace(Regex("[^A-Za-z0-9._-]"), "_")
            val params = "{\"group_name\":\"${entry.group_name}\",\"teacher_name\":\"${entry.teacher_name}\",\"room_name\":\"${entry.room_name}\",\"format\":2}"
            val raw = runCatching {
                RustBridge.nativeFetchRaw(stateDir, cacheDir, "seatingplan-$key", "EduLink.SeatingPlan", params)
            }.getOrNull()
            val (d, _) = decodeSurface<SeatingPlanDetail>(raw)
            val err = if (d == null) {
                raw?.let { runCatching { json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() }
                    ?: lastDecodeError ?: "seating plan fetch failed (no response)"
            } else null
            recoverExpiredSession(err)
            withContext(Dispatchers.Main) { onDone(d, err) }
        }
    }

    private fun recoverExpiredSession(message: String?) {
        if (message != null && sessionNeedsSignIn(listOf(message))) {
            _ui.value = _ui.value.copy(sessionExpired = true, showLogin = true,
                error = "Your school session expired. Sign in again. Your saved data is kept.")
        }
    }

    /** Wave-2 extras: the bridge is cache-first with a TTL and serves the
     * vendor fixtures in demo mode, so every call goes through fetchRaw.
     * Errors surface the server's own text, never a guess. */
    private fun <T> wave2(cache: String, method: String, params: String, decode: (String?) -> T?, onDone: (T?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeFetchRaw(stateDir, cacheDir, cache, method, params) }.getOrNull()
            val d = decode(raw)
            val err = if (d == null) {
                raw?.let { runCatching { json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() }
                    ?: lastDecodeError ?: "fetch failed (no response)"
            } else null
            recoverExpiredSession(err)
            withContext(Dispatchers.Main) { onDone(d, err) }
        }
    }

    // OG sends for="learner"|"parent"; without it the API 400s
    // ("Missing required property [for]"). All our accounts are learners.
    fun forms(onDone: (FormsData?, String?) -> Unit) = wave2("forms", "EduLink.Forms", "{\"for\":\"learner\",\"format\":2}", { decodeSurface<FormsData>(it).first }, onDone)
    fun formDetail(id: Int, onDone: (FormDetailData?, String?) -> Unit) = wave2("form-$id", "EduLink.Form", "{\"form_id\":$id,\"format\":2}", { decodeSurface<FormDetailData>(it).first }, onDone)
    fun absences(onDone: (AbsencesData?, String?) -> Unit) = wave2("absences", "EduLink.AbsenceManagement", "{\"format\":2}", { decodeSurface<AbsencesData>(it).first }, onDone)
    fun detentions(onDone: (DetentionsData?, String?) -> Unit) = wave2("detentions", "EduLink.DetentionSessions", "{\"format\":2}", { decodeSurface<DetentionsData>(it).first }, onDone)
    fun detentionDetail(id: Int, onDone: (DetentionDetailData?, String?) -> Unit) = wave2("detention-$id", "EduLink.DetentionSession", "{\"detention_session_id\":$id,\"format\":2}", { decodeSurface<DetentionDetailData>(it).first }, onDone)
    fun moments(onDone: (MomentsData?, String?) -> Unit) = wave2("moments", "EduLink.Moments", "{\"limit\":30}", { decodeSurface<MomentsData>(it).first }, onDone)
    fun assessment(onDone: (AssessmentData?, String?) -> Unit) = wave2("assessment", "EduLink.AssessmentGrids", "{\"format\":2}", { decodeSurface<AssessmentData>(it).first }, onDone)
    fun hasCached(name: String): Boolean = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, name) }.getOrNull() != null

    /** Report card is teacher-side on real logins; demo caches it, so it is
     * read from cache only and the menu entry is gated on hasCached. */
    fun reportCard(onDone: (ReportCardData?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeGetCached(stateDir, cacheDir, "reportcard") }.getOrNull()
            val (d, _) = decodeSurface<ReportCardData>(raw)
            withContext(Dispatchers.Main) { onDone(d, if (d == null) "no report card cached" else null) }
        }
    }

    /** Detail fetch; second callback value is the REAL error when it fails, never a guess. */
    fun homeworkDetail(id: String, source: String = "EduLink", onDone: (HomeworkDetail?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = runCatching { RustBridge.nativeHwDetail(stateDir, cacheDir, id, source) }.getOrNull()
            val (detail, _) = decodeSurface<HomeworkDetail>(raw)
            val listItem = (_ui.value.homework?.homework?.current.orEmpty() + _ui.value.homework?.homework?.past.orEmpty()).firstOrNull { it.idString() == id }
            val d = detail?.withListAttachments(listItem)
            val err = if (d == null) {
                raw?.let {
                    runCatching {
                        json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull()
                } ?: lastDecodeError ?: "detail fetch failed (no response)"
            } else null
            recoverExpiredSession(err)
            withContext(Dispatchers.Main) { onDone(d, err) }
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
