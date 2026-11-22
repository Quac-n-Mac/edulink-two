//! The Edulink Two client. Read-only: the only methods it can call are
//! the read methods listed in `methods`. All main surfaces prefetch in
//! parallel (login, sync, TUI refresh) so every screen renders instantly.

use crate::models::*;
use crate::transport::{HttpTransport, Transport};
use anyhow::{Context, Result};
use serde_json::{json, Value};
use std::future::Future;
use std::time::Instant;

/// Every wire method this client can send. The list is the read-only
/// guarantee: there is no code path to any write method.
pub mod methods {
    pub const LOGIN: &str = "EduLink.Login";
    pub const LOGIN_CONTEXT: &str = "EduLink.LoginContext";
    pub const STATUS: &str = "EduLink.Status";
    pub const HOMEWORK: &str = "EduLink.Homework";
    pub const HOMEWORK_DETAILS: &str = "EduLink.HomeworkDetails";
    /// Read-only attachment download (fetch = read).
    pub const HOMEWORK_ATTACHMENT_FETCH: &str = "EduLink.HomeworkAttachmentFetch";
    pub const TIMETABLE: &str = "EduLink.Timetable";
    pub const ACHIEVEMENT: &str = "EduLink.Achievement";
    pub const BEHAVIOUR: &str = "EduLink.Behaviour";
    pub const ATTENDANCE: &str = "EduLink.Attendance";
    pub const EXAMS: &str = "EduLink.Exams";
    pub const NOTICEBOARD: &str = "EduLink.Noticeboard";
    pub const NOTICEBOARD_FILE: &str = "EduLink.NoticeboardFile";
    pub const CALENDAR: &str = "EduLink.Calendar";
    pub const CLUBS: &str = "EduLink.Clubs";
    pub const PERSONAL: &str = "EduLink.Personal";
    pub const INBOX: &str = "Communicator.Inbox";
    pub const OUTBOX: &str = "Communicator.Outbox";
    /// Pre-login school resolution on the provisioning service.
    pub const SCHOOL_FROM_CODE: &str = "School.FromCode";
    /// Pre-login school metadata (name + crest) on the school's server.
    pub const SCHOOL_DETAILS: &str = "EduLink.SchoolDetails";
}

/// A school resolved from its code/postcode, exactly like the stock
/// app's login flow: School.FromCode on the provisioning service gives
/// the API host and establishment id, then EduLink.Login runs there.
pub struct SchoolInfo {
    pub host: String,
    pub school_id: i64,
}

/// Resolve a school code or postcode to its API host + establishment id.
/// Public endpoint, no auth - the stock app calls it before credentials.
pub async fn resolve_school(code: &str) -> Result<SchoolInfo> {
    let t = HttpTransport::new("provisioning.edulinkone.com")?;
    let v = t
        .call(methods::SCHOOL_FROM_CODE, json!({"code": code.trim()}))
        .await
        .context("school lookup failed")?;
    let school = v.get("school").context("no school in lookup response")?;
    let server = school
        .get("server")
        .and_then(|s| s.as_str())
        .context("school.server missing")?;
    let host = server
        .trim_start_matches("https://")
        .trim_start_matches("http://")
        .trim_end_matches("/api/")
        .trim_end_matches('/')
        .to_string();
    let school_id = school
        .get("school_id")
        .and_then(|s| s.as_i64())
        .context("school_id missing")?;
    Ok(SchoolInfo { host, school_id })
}

/// School metadata for the credentials screen: name + base64 crest.
/// Public endpoint, no auth - the stock app shows it before login.
pub async fn school_details(host: &str, establishment_id: i64) -> Result<(String, Option<String>)> {
    let t = HttpTransport::new(host)?;
    let v = t
        .call(
            methods::SCHOOL_DETAILS,
            json!({"establishment_id": establishment_id, "from_app": true, "format": 2}),
        )
        .await
        .context("school details failed")?;
    let est = v.get("establishment").cloned().unwrap_or(json!({}));
    let name = est.get("name").and_then(|n| n.as_str()).unwrap_or("").to_string();
    let logo = est.get("logo").and_then(|l| l.as_str()).map(str::to_string);
    Ok((name, logo))
}

pub struct Client<T: Transport> {
    transport: T,
    pub host: String,
    pub username: String,
}

/// One surface's prefetch outcome.
pub struct Surface<T> {
    pub result: Result<T>,
    pub elapsed_ms: u128,
}

pub struct Prefetch {
    pub homework: Surface<HomeworkData>,
    pub timetable: Surface<TimetableData>,
    pub achievement: Surface<AchievementData>,
    pub behaviour: Surface<BehaviourData>,
    pub attendance: Surface<AttendanceData>,
    pub exams: Surface<ExamsData>,
    pub noticeboard: Surface<NoticeboardData>,
    pub calendar: Surface<CalendarData>,
    pub clubs: Surface<ClubsData>,
    pub personal: Surface<PersonalData>,
    pub inbox: Surface<MessagesData>,
    pub total_ms: u128,
}

async fn timed<F, O>(f: F) -> (Result<O>, u128)
where
    F: Future<Output = Result<O>>,
{
    let t = Instant::now();
    let r = f.await;
    (r, t.elapsed().as_millis())
}

impl<T: Transport> Client<T> {
    pub fn new(transport: T, host: impl Into<String>, username: impl Into<String>) -> Self {
        Self {
            transport,
            host: host.into(),
            username: username.into(),
        }
    }

    /// Log in and immediately prefetch every main surface in parallel,
    /// so opening any section is instant. Returns the login data plus
    /// all surfaces with per-surface timings.
    pub async fn login(&self, password: &str, establishment_id: i64, learner_id: Option<&str>, date: &str) -> Result<(LoginData, Prefetch)> {
        let params = json!({
            "username": self.username,
            "password": password,
            "establishment_id": establishment_id,
            "from_app": true,
            "fcm_token_old": "none",
            "format": 2,
        });
        let result = self.transport.call(methods::LOGIN, params).await?;
        let mut login: LoginData = serde_json::from_value(result).context("parse login")?;
        self.transport.set_token(&login.authtoken);

        // Handshake pair in parallel, then the surfaces in parallel.
        // The real Login response has no identity: user + establishment
        // come from LoginContext. Merge them into the login data.
        let (ctx, _status) = tokio::join!(
            self.transport.call(
                methods::LOGIN_CONTEXT,
                json!({"last_message": "", "redirect": "", "format": 2})
            ),
            self.transport.call(methods::STATUS, json!({"last_message": ""})),
        );
        let ctx: LoginContextData =
            serde_json::from_value(ctx.context("login context")?).context("parse login context")?;
        if login.user.is_none() {
            login.user = ctx.user;
        }
        if login.establishment.is_none() {
            login.establishment = ctx.establishment;
        }
        if login.user.is_none() {
            anyhow::bail!("login: server returned no user identity");
        }

        let learner = learner_id
            .map(str::to_string)
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| login.user.as_ref().unwrap().id.to_string());
        let prefetch = self.prefetch_all(&learner, date).await;
        Ok((login, prefetch))
    }

    /// Fetch all main surfaces in parallel. Never fails wholesale:
    /// per-surface results carry their own errors.
    pub async fn prefetch_all(&self, learner_id: &str, date: &str) -> Prefetch {
        let t = Instant::now();
        let (hw, tb, ach, beh, att, ex, nb, cal, cl, per, inbox) = tokio::join!(
            timed(self.homework()),
            timed(self.timetable(learner_id, date)),
            timed(self.achievement(learner_id)),
            timed(self.behaviour(learner_id)),
            timed(self.attendance(learner_id)),
            timed(self.exams(learner_id)),
            timed(self.noticeboard()),
            timed(self.calendar(learner_id, date)),
            timed(self.clubs()),
            timed(self.personal()),
            timed(self.inbox()),
        );
        Prefetch {
            homework: Surface { result: hw.0, elapsed_ms: hw.1 },
            timetable: Surface { result: tb.0, elapsed_ms: tb.1 },
            achievement: Surface { result: ach.0, elapsed_ms: ach.1 },
            behaviour: Surface { result: beh.0, elapsed_ms: beh.1 },
            attendance: Surface { result: att.0, elapsed_ms: att.1 },
            exams: Surface { result: ex.0, elapsed_ms: ex.1 },
            noticeboard: Surface { result: nb.0, elapsed_ms: nb.1 },
            calendar: Surface { result: cal.0, elapsed_ms: cal.1 },
            clubs: Surface { result: cl.0, elapsed_ms: cl.1 },
            personal: Surface { result: per.0, elapsed_ms: per.1 },
            inbox: Surface { result: inbox.0, elapsed_ms: inbox.1 },
            total_ms: t.elapsed().as_millis(),
        }
    }

    pub async fn homework(&self) -> Result<HomeworkData> {
        let v = self
            .transport
            .call(methods::HOMEWORK, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse homework")
    }

    pub async fn homework_details(&self, id: &str, source: &str) -> Result<HomeworkDetail> {
        let v = self
            .transport
            .call(
                methods::HOMEWORK_DETAILS,
                json!({"homework_id": id, "source": source, "format": 2}),
            )
            .await?;
        // The live API freely sends nulls and numbers where strings/vecs
        // are expected; coerce before typed parsing so one odd field
        // does not fail the whole detail fetch.
        let mut v = v;
        if let Some(hw) = v.get_mut("homework").and_then(|h| h.as_object_mut()) {
            for key in ["activity", "subject", "description", "due_date", "available_date"] {
                if let Some(val) = hw.get_mut(key) {
                    let fixed = match &*val {
                        Value::Null => Some(Value::String(String::new())),
                        Value::Number(n) => Some(Value::String(n.to_string())),
                        Value::Bool(b) => Some(Value::String(b.to_string())),
                        _ => None,
                    };
                    if let Some(f) = fixed {
                        *val = f;
                    }
                }
            }
            if matches!(hw.get("attachments"), Some(Value::Null)) {
                hw.insert("attachments".to_string(), json!([]));
            }
            if matches!(hw.get("set_by"), Some(Value::Null)) {
                hw.remove("set_by");
            }
        }
        serde_json::from_value(v).context("parse homework details")
    }

    /// Fetch one homework attachment. Returns the raw attachment object
    /// (filename, mime_type, base64 data) for the caller to persist.
    pub async fn homework_attachment(&self, attachment_id: &str) -> Result<Value> {
        let v = self
            .transport
            .call(
                methods::HOMEWORK_ATTACHMENT_FETCH,
                json!({"attachment_id": attachment_id, "format": 2}),
            )
            .await?;
        Ok(v.get("attachment").cloned().unwrap_or(v))
    }

    /// Fetch one noticeboard document (base64 content inside `document`).
    pub async fn noticeboard_file(&self, id: &str) -> Result<Value> {
        let v = self
            .transport
            .call(
                methods::NOTICEBOARD_FILE,
                json!({"id": id, "format": 2}),
            )
            .await?;
        Ok(v.get("document").cloned().unwrap_or(v))
    }

    pub async fn timetable(&self, learner_id: &str, date: &str) -> Result<TimetableData> {
        let v = self
            .transport
            .call(
                methods::TIMETABLE,
                json!({"date": date, "learner_id": learner_id, "format": 2}),
            )
            .await?;
        serde_json::from_value(v).context("parse timetable")
    }

    pub async fn achievement(&self, learner_id: &str) -> Result<AchievementData> {
        let v = self
            .transport
            .call(
                methods::ACHIEVEMENT,
                json!({"learner_id": learner_id, "format": 2}),
            )
            .await?;
        serde_json::from_value(v).context("parse achievement")
    }

    pub async fn behaviour(&self, learner_id: &str) -> Result<BehaviourData> {
        let v = self
            .transport
            .call(
                methods::BEHAVIOUR,
                json!({"learner_id": learner_id, "format": 2}),
            )
            .await?;
        serde_json::from_value(v).context("parse behaviour")
    }

    pub async fn attendance(&self, learner_id: &str) -> Result<AttendanceData> {
        let v = self
            .transport
            .call(methods::ATTENDANCE, json!({"learner_id": learner_id, "format": 2}))
            .await?;
        serde_json::from_value(v).context("parse attendance")
    }

    pub async fn exams(&self, learner_id: &str) -> Result<ExamsData> {
        let v = self
            .transport
            .call(methods::EXAMS, json!({"learner_id": learner_id, "format": 2}))
            .await?;
        serde_json::from_value(v).context("parse exams")
    }

    pub async fn noticeboard(&self) -> Result<NoticeboardData> {
        let v = self
            .transport
            .call(methods::NOTICEBOARD, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse noticeboard")
    }

    pub async fn calendar(&self, learner_id: &str, date: &str) -> Result<CalendarData> {
        let v = self
            .transport
            .call(
                methods::CALENDAR,
                json!({"learner_id": learner_id, "date": date, "format": 2}),
            )
            .await?;
        serde_json::from_value(v).context("parse calendar")
    }

    pub async fn clubs(&self) -> Result<ClubsData> {
        let v = self
            .transport
            .call(methods::CLUBS, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse clubs")
    }

    pub async fn personal(&self) -> Result<PersonalData> {
        let v = self
            .transport
            .call(methods::PERSONAL, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse personal")
    }

    /// Read-only inbox listing. Never calls MessageMarkRead.
    pub async fn inbox(&self) -> Result<MessagesData> {
        let v = self
            .transport
            .call(methods::INBOX, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse inbox")
    }

    /// Read-only outbox listing.
    pub async fn outbox(&self) -> Result<MessagesData> {
        let v = self
            .transport
            .call(methods::OUTBOX, json!({"format": 2}))
            .await?;
        serde_json::from_value(v).context("parse outbox")
    }

    pub async fn status(&self) -> Result<StatusData> {
        let v = self
            .transport
            .call(methods::STATUS, json!({"last_message": ""}))
            .await?;
        serde_json::from_value(v).context("parse status")
    }
}
