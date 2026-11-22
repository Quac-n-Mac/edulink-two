//! Offline demo mode: the official Edulink One demo content (the vendor's
//! own fictional fixtures shipped in the stock app), loaded into the cache
//! under the demo account. No network is touched in demo mode.
//! Green Abbey School / Student Demo are the vendor's fictional demo school
//! and learner, not real records.

use crate::cache::Cache;
use crate::session::{AccountStore, Session};
use anyhow::{Context, Result};
use serde_json::Value;
use std::path::PathBuf;

pub const DEMO_HOST: &str = "demo.offline";

macro_rules! fixture {
    ($name:literal) => {
        include_str!(concat!("../fixtures/ogdemo/", $name))
    };
}

fn result_of(raw: &str) -> Result<Value> {
    let v: Value = serde_json::from_str(raw).context("demo fixture parse")?;
    Ok(v.get("result").cloned().unwrap_or(v))
}

pub fn demo_session() -> Session {
    let lc = result_of(fixture!("logincontext.json")).unwrap_or(Value::Null);
    let user = lc.get("user").cloned().unwrap_or(Value::Null);
    let school = lc
        .get("establishment")
        .and_then(|e| e.get("name"))
        .and_then(|n| n.as_str())
        .unwrap_or("Demo School")
        .to_string();
    let name = format!(
        "{} {}",
        user.get("forename").and_then(|s| s.as_str()).unwrap_or("Demo"),
        user.get("surname").and_then(|s| s.as_str()).unwrap_or("Learner")
    );
    let uid = user
        .get("id")
        .and_then(|i| i.as_i64().map(|n| n.to_string()).or_else(|| i.as_str().map(String::from)))
        .unwrap_or_else(|| "1".into());
    Session {
        host: DEMO_HOST.into(),
        authtoken: "demo".into(),
        username: "learner".into(),
        user_id: uid.clone(),
        learner_id: uid,
        display_name: name.trim().into(),
        school,
        school_code: "demo".into(),
        school_id: 2033,
        saved_at: chrono::Utc::now(),
    }
}

pub fn demo_avatar_b64() -> Option<String> {
    let lc = result_of(fixture!("logincontext.json")).ok()?;
    lc.get("user")
        .and_then(|u| u.get("avatar"))
        .and_then(|a| a.get("photo"))
        .and_then(|p| p.as_str())
        .map(String::from)
}

pub fn is_demo(session: &Session) -> bool {
    session.host == DEMO_HOST
}

/// Create (or refresh) the demo account and fill every cached surface
/// with the stock demo content. No network is touched.
pub async fn demo_login(state_dir: PathBuf, cache_dir: PathBuf) -> Result<Value> {
    let session = demo_session();
    let mut store = AccountStore::load(&state_dir)?;
    store.upsert(session.clone());
    store.save(&state_dir)?;
    let cache = Cache::new(&cache_dir)?.for_user(&session.key())?;

    for (name, raw) in [
        ("homework", fixture!("homework.json")),
        ("timetable", fixture!("timetable.json")),
        ("achievement", fixture!("achievement.json")),
        ("behaviour", fixture!("behaviour.json")),
        ("attendance", fixture!("attendance.json")),
        ("exams", fixture!("exams.json")),
        ("noticeboard", fixture!("noticeboard.json")),
        ("calendar", fixture!("calendar.json")),
        ("clubs", fixture!("clubs.json")),
        ("personal", fixture!("personal.json")),
        ("inbox", fixture!("inbox.json")),
        ("outbox", fixture!("outbox.json")),
        ("registers", fixture!("registers.json")),
        ("seatingplans", fixture!("seatingplans.json")),
        ("medical", fixture!("medical.json")),
        ("catering", fixture!("catering.json")),
        ("documents", fixture!("documents.json")),
        ("externallinks", fixture!("externallinks.json")),
        ("reportcard", fixture!("reportcard.json")),
        ("teachers", fixture!("teachers.json")),
        ("forms", fixture!("forms.json")),
        ("form-demo", fixture!("form.json")),
        ("absences-demo", fixture!("absence.json")),
        ("detentions", fixture!("detentions.json")),
        ("detention-demo", fixture!("detention.json")),
        ("moments-demo", fixture!("moments.json")),
        ("assessment-demo", fixture!("assessment.json")),
    ] {
        cache.write(name, &result_of(raw)?).await?;
    }

    // Homework detail: the stock demo detail response, cached under every
    // current homework id so any card tap opens it.
    let detail = result_of(fixture!("hwdetail.json"))?;
    if let Some(items) = result_of(fixture!("homework.json"))?
        .get("homework")
        .and_then(|h| h.get("current"))
        .and_then(|c| c.as_array())
        .cloned()
    {
        for item in items {
            if let Some(id) = item.get("id").and_then(|i| i.as_i64().map(|n| n.to_string()).or_else(|| i.as_str().map(String::from))) {
                cache.write(&format!("hwdetail-{id}"), &detail).await?;
            }
        }
    }
    // The full class register (with marks) from the demo, under its own id.
    cache.write("register-full", &result_of(fixture!("register.json"))?).await?;
    cache.write("seatingplan-full", &result_of(fixture!("seatingplan.json"))?).await?;

    Ok(serde_json::json!({
        "ok": true,
        "key": session.key(),
        "display_name": session.display_name,
        "school": session.school,
        "avatar_b64": demo_avatar_b64(),
    }))
}
