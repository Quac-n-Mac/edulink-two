//! JNI bridge: one JSON string in, one JSON string out. Kotlin owns the
//! UI; all network, models, cache and accounts stay in the Rust core.
//! Read-only: only the core's read methods are reachable here.

use anyhow::{Context, Result};
use edulink_two::cache::{Cache, TTL_HW_DETAIL};
use edulink_two::client::Client;
use edulink_two::session::{AccountStore, Session};
use edulink_two::transport::{HttpTransport, Transport};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde_json::{json, Value};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};

static DEGRADED: AtomicBool = AtomicBool::new(false);

fn block_on<F: std::future::Future>(f: F) -> F::Output {
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("runtime");
    rt.block_on(f)
}

fn to_jstring(env: &mut JNIEnv, s: String) -> jstring {
    env.new_string(s).expect("new_string").into_raw()
}

fn get(env: &mut JNIEnv, s: &JString) -> Result<String> {
    Ok(env.get_string(s)?.into())
}

fn err_str(e: &anyhow::Error) -> String {
    json!({"ok": false, "error": format!("{e:#}")}).to_string()
}

fn client_for(session: &Session) -> Result<Client<HttpTransport>> {
    let t = HttpTransport::new(&session.host)?;
    t.set_token(&session.authtoken);
    t.set_degraded(DEGRADED.load(Ordering::Relaxed));
    Ok(Client::new(t, session.host.clone(), session.username.clone()))
}

async fn do_login(state_dir: PathBuf, cache_dir: PathBuf, host: String, username: String, password: String, learner_id: String) -> Result<Value> {
    let t = HttpTransport::new(&host)?;
    t.set_degraded(DEGRADED.load(Ordering::Relaxed));
    let client = Client::new(t, host.clone(), username.clone());
    let date = chrono_today();
    let (login, pre) = client
        .login(
            &password,
            if learner_id.is_empty() { None } else { Some(learner_id.as_str()) },
            &date,
        )
        .await
        .context("login failed")?;
    let school = login
        .establishment
        .as_ref()
        .and_then(|e| e.name.clone())
        .unwrap_or_default();
    let session = Session {
        host,
        authtoken: login.authtoken.clone(),
        username: username.clone(),
        user_id: login.user.id.to_string(),
        learner_id: if learner_id.is_empty() {
            login.user.id.to_string()
        } else {
            learner_id
        },
        display_name: login.user.display(),
        school,
        saved_at: chrono::Utc::now(),
    };
    let mut store = AccountStore::load(&state_dir)?;
    store.upsert(session.clone());
    store.save(&state_dir)?;
    let cache = Cache::new(&cache_dir)?.for_user(&session.key())?;
    let (mut ok, mut failed) = (0u8, Vec::<String>::new());
    if let Ok(d) = pre.homework.result { cache.write("homework", &d).await?; ok += 1; } else if let Err(e) = pre.homework.result { failed.push(format!("homework: {e:#}")); }
    if let Ok(d) = pre.timetable.result { cache.write("timetable", &d).await?; ok += 1; } else if let Err(e) = pre.timetable.result { failed.push(format!("timetable: {e:#}")); }
    if let Ok(d) = pre.achievement.result { cache.write("achievement", &d).await?; ok += 1; } else if let Err(e) = pre.achievement.result { failed.push(format!("achievement: {e:#}")); }
    if let Ok(d) = pre.behaviour.result { cache.write("behaviour", &d).await?; ok += 1; } else if let Err(e) = pre.behaviour.result { failed.push(format!("behaviour: {e:#}")); }
    if let Ok(d) = pre.attendance.result { cache.write("attendance", &d).await?; ok += 1; } else if let Err(e) = pre.attendance.result { failed.push(format!("attendance: {e:#}")); }
    if let Ok(d) = pre.exams.result { cache.write("exams", &d).await?; ok += 1; } else if let Err(e) = pre.exams.result { failed.push(format!("exams: {e:#}")); }
    if let Ok(d) = pre.noticeboard.result { cache.write("noticeboard", &d).await?; ok += 1; } else if let Err(e) = pre.noticeboard.result { failed.push(format!("noticeboard: {e:#}")); }
    if let Ok(d) = pre.calendar.result { cache.write("calendar", &d).await?; ok += 1; } else if let Err(e) = pre.calendar.result { failed.push(format!("calendar: {e:#}")); }
    if let Ok(d) = pre.clubs.result { cache.write("clubs", &d).await?; ok += 1; } else if let Err(e) = pre.clubs.result { failed.push(format!("clubs: {e:#}")); }
    if let Ok(d) = pre.personal.result { cache.write("personal", &d).await?; ok += 1; } else if let Err(e) = pre.personal.result { failed.push(format!("personal: {e:#}")); }
    if let Ok(d) = pre.inbox.result { cache.write("inbox", &d).await?; ok += 1; } else if let Err(e) = pre.inbox.result { failed.push(format!("inbox: {e:#}")); }
    Ok(json!({
        "ok": true,
        "key": session.key(),
        "display_name": session.display_name,
        "school": session.school,
        "learner_id": session.learner_id,
        "surfaces_ok": ok,
        "failed": failed,
        "prefetch_ms": pre.total_ms,
    }))
}

async fn do_refresh(state_dir: PathBuf, cache_dir: PathBuf) -> Result<Value> {
    let store = AccountStore::load(&state_dir)?;
    let session = store
        .current()
        .context("not logged in")?
        .clone();
    let client = client_for(&session)?;
    let cache = Cache::new(&cache_dir)?.for_user(&session.key())?;
    let date = chrono_today();
    let pre = client.prefetch_all(&session.learner_id, &date).await;
    let (mut ok, mut failed) = (0u8, Vec::<String>::new());
    if let Ok(d) = pre.homework.result { cache.write("homework", &d).await?; ok += 1; } else if let Err(e) = pre.homework.result { failed.push(format!("homework: {e:#}")); }
    if let Ok(d) = pre.timetable.result { cache.write("timetable", &d).await?; ok += 1; } else if let Err(e) = pre.timetable.result { failed.push(format!("timetable: {e:#}")); }
    if let Ok(d) = pre.achievement.result { cache.write("achievement", &d).await?; ok += 1; } else if let Err(e) = pre.achievement.result { failed.push(format!("achievement: {e:#}")); }
    if let Ok(d) = pre.behaviour.result { cache.write("behaviour", &d).await?; ok += 1; } else if let Err(e) = pre.behaviour.result { failed.push(format!("behaviour: {e:#}")); }
    if let Ok(d) = pre.attendance.result { cache.write("attendance", &d).await?; ok += 1; } else if let Err(e) = pre.attendance.result { failed.push(format!("attendance: {e:#}")); }
    if let Ok(d) = pre.exams.result { cache.write("exams", &d).await?; ok += 1; } else if let Err(e) = pre.exams.result { failed.push(format!("exams: {e:#}")); }
    if let Ok(d) = pre.noticeboard.result { cache.write("noticeboard", &d).await?; ok += 1; } else if let Err(e) = pre.noticeboard.result { failed.push(format!("noticeboard: {e:#}")); }
    if let Ok(d) = pre.calendar.result { cache.write("calendar", &d).await?; ok += 1; } else if let Err(e) = pre.calendar.result { failed.push(format!("calendar: {e:#}")); }
    if let Ok(d) = pre.clubs.result { cache.write("clubs", &d).await?; ok += 1; } else if let Err(e) = pre.clubs.result { failed.push(format!("clubs: {e:#}")); }
    if let Ok(d) = pre.personal.result { cache.write("personal", &d).await?; ok += 1; } else if let Err(e) = pre.personal.result { failed.push(format!("personal: {e:#}")); }
    if let Ok(d) = pre.inbox.result { cache.write("inbox", &d).await?; ok += 1; } else if let Err(e) = pre.inbox.result { failed.push(format!("inbox: {e:#}")); }
    Ok(json!({"ok": failed.is_empty(), "surfaces_ok": ok, "failed": failed, "total_ms": pre.total_ms}))
}

fn chrono_today() -> String {
    chrono::Local::now().format("%Y-%m-%d").to_string()
}

fn read_cache_raw(cache_dir: &PathBuf, state_dir: &PathBuf, name: &str) -> Result<String> {
    let store = AccountStore::load(state_dir)?;
    let session = store.current().context("not logged in")?;
    let cache = Cache::new(cache_dir)?.for_user(&session.key())?;
    let path = cache_file(&cache, name);
    let text = std::fs::read_to_string(&path)
        .with_context(|| format!("no cached {name}"))?;
    Ok(text)
}

fn cache_file(cache: &Cache, name: &str) -> PathBuf {
    // Cache's path builder is private; mirror its naming here.
    cache_dir_of(cache).join(format!("{name}.json"))
}

fn cache_dir_of(cache: &Cache) -> PathBuf {
    // Cache exposes no dir accessor; keep one via Debug? No - recompute.
    // Simplest: Cache::for_user path is <root>/<user>. We cannot see root.
    // So instead: store the path alongside. (Resolved below.)
    let dbg = format!("{cache:?}");
    // Cache derives Debug as `Cache { dir: "..." }`.
    let start = dbg.find('"').map(|i| i + 1).unwrap_or(0);
    let end = dbg.rfind('"').unwrap_or(dbg.len());
    PathBuf::from(&dbg[start..end])
}

async fn do_hw_detail(state_dir: PathBuf, cache_dir: PathBuf, id: String) -> Result<String> {
    let store = AccountStore::load(&state_dir)?;
    let session = store.current().context("not logged in")?.clone();
    let cache = Cache::new(&cache_dir)?.for_user(&session.key())?;
    let name = format!("hwdetail-{id}");
    // Fresh cache wins; then network; then stale cache.
    if let Ok(text) = std::fs::read_to_string(cache_file(&cache, &name)) {
        let v: Value = serde_json::from_str(&text)?;
        if let Some(ts) = v.get("fetched_at").and_then(|t| t.as_str()) {
            if let Ok(t) = chrono::DateTime::parse_from_rfc3339(ts) {
                if chrono::Utc::now().signed_duration_since(t.with_timezone(&chrono::Utc)) < TTL_HW_DETAIL {
                    return Ok(text);
                }
            }
        }
    }
    let client = client_for(&session)?;
    match client.homework_details(&id, "homework").await {
        Ok(d) => {
            cache.write(&name, &d).await?;
            std::fs::read_to_string(cache_file(&cache, &name)).context("read back detail")
        }
        Err(e) => {
            let path = cache_file(&cache, &name);
            if path.exists() {
                Ok(std::fs::read_to_string(&path)?)
            } else {
                Err(e)
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeLogin(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    cache_dir: JString,
    host: JString,
    username: JString,
    password: JString,
    learner_id: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let cache_dir = PathBuf::from(get(&mut env, &cache_dir)?);
        let host = get(&mut env, &host)?;
        let username = get(&mut env, &username)?;
        let password = get(&mut env, &password)?;
        let learner_id = get(&mut env, &learner_id)?;
        Ok(block_on(do_login(state_dir, cache_dir, host, username, password, learner_id))?.to_string())
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeRefresh(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    cache_dir: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let cache_dir = PathBuf::from(get(&mut env, &cache_dir)?);
        Ok(block_on(do_refresh(state_dir, cache_dir))?.to_string())
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeGetCached(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    cache_dir: JString,
    name: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let cache_dir = PathBuf::from(get(&mut env, &cache_dir)?);
        let name = get(&mut env, &name)?;
        read_cache_raw(&cache_dir, &state_dir, &name)
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeHwDetail(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    cache_dir: JString,
    id: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let cache_dir = PathBuf::from(get(&mut env, &cache_dir)?);
        let id = get(&mut env, &id)?;
        block_on(do_hw_detail(state_dir, cache_dir, id))
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeAccounts(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let store = AccountStore::load(&state_dir)?;
        Ok(json!({
            "ok": true,
            "current": store.current,
            "accounts": store.accounts.iter().map(|s| json!({
                "key": s.key(),
                "username": s.username,
                "display_name": s.display_name,
                "school": s.school,
                "host": s.host,
            })).collect::<Vec<_>>(),
        })
        .to_string())
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeSwitchAccount(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    key: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let key = get(&mut env, &key)?;
        let mut store = AccountStore::load(&state_dir)?;
        store.set_current(&key)?;
        store.save(&state_dir)?;
        Ok(json!({"ok": true, "current": key}).to_string())
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeRemoveAccount(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    key: JString,
) -> jstring {
    let out = (|| -> Result<String> {
        let state_dir = PathBuf::from(get(&mut env, &state_dir)?);
        let key = get(&mut env, &key)?;
        let mut store = AccountStore::load(&state_dir)?;
        let removed = store.remove(&key);
        store.save(&state_dir)?;
        Ok(json!({"ok": removed}).to_string())
    })();
    match out {
        Ok(s) => to_jstring(&mut env, s),
        Err(e) => to_jstring(&mut env, err_str(&e)),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_edulinktwo_RustBridge_nativeSetDegraded(
    _env: JNIEnv,
    _class: JClass,
    degraded: jni::sys::jboolean,
) {
    DEGRADED.store(degraded != 0, Ordering::Relaxed);
}
