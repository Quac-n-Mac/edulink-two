//! The Edulink Two client. Read-only: the only methods it can call are
//! the read methods listed in `methods`. All main surfaces prefetch in
//! parallel (login, sync, TUI refresh) so every screen renders instantly.

use crate::models::*;
use crate::transport::Transport;
use anyhow::{Context, Result};
use serde_json::json;
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
    pub const TIMETABLE: &str = "EduLink.Timetable";
    pub const ACHIEVEMENT: &str = "EduLink.Achievement";
    pub const BEHAVIOUR: &str = "EduLink.Behaviour";
    pub const ATTENDANCE: &str = "EduLink.Attendance";
    pub const EXAMS: &str = "EduLink.Exams";
    pub const NOTICEBOARD: &str = "EduLink.Noticeboard";
    pub const CALENDAR: &str = "EduLink.Calendar";
    pub const CLUBS: &str = "EduLink.Clubs";
    pub const PERSONAL: &str = "EduLink.Personal";
    pub const INBOX: &str = "Communicator.Inbox";
    pub const OUTBOX: &str = "Communicator.Outbox";
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
    pub async fn login(&self, password: &str, learner_id: Option<&str>, date: &str) -> Result<(LoginData, Prefetch)> {
        let params = json!({
            "username": self.username,
            "password": password,
            "from_app": false,
            "ui_format": "2",
            "fcm_token_old": "none",
            "format": 2,
        });
        let result = self.transport.call(methods::LOGIN, params).await?;
        let login: LoginData = serde_json::from_value(result).context("parse login")?;
        self.transport.set_token(&login.authtoken);

        // Handshake pair in parallel, then the surfaces in parallel.
        let (ctx, _status) = tokio::join!(
            self.transport.call(
                methods::LOGIN_CONTEXT,
                json!({"last_message": "", "redirect": "", "format": 2})
            ),
            self.transport.call(methods::STATUS, json!({"last_message": ""})),
        );
        ctx.context("login context")?;

        let learner = learner_id
            .map(str::to_string)
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| login.user.id.to_string());
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
        serde_json::from_value(v).context("parse homework details")
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
