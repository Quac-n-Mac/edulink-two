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
        let (hw, tb, ach, beh) = tokio::join!(
            timed(self.homework()),
            timed(self.timetable(learner_id, date)),
            timed(self.achievement(learner_id)),
            timed(self.behaviour(learner_id)),
        );
        Prefetch {
            homework: Surface { result: hw.0, elapsed_ms: hw.1 },
            timetable: Surface { result: tb.0, elapsed_ms: tb.1 },
            achievement: Surface { result: ach.0, elapsed_ms: ach.1 },
            behaviour: Surface { result: beh.0, elapsed_ms: beh.1 },
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

    pub async fn status(&self) -> Result<StatusData> {
        let v = self
            .transport
            .call(methods::STATUS, json!({"last_message": ""}))
            .await?;
        serde_json::from_value(v).context("parse status")
    }
}
