//! Data models mapped from the vendor's own demo response shapes
//! (assets/public/jsons in the APK) and live read-only captures.
//! Field names match the wire format exactly.

use serde::{Deserialize, Serialize};
use std::fmt;

/// An id on the wire is sometimes a string, sometimes a number.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Id {
    S(String),
    N(i64),
}
impl fmt::Display for Id {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Id::S(s) => write!(f, "{s}"),
            Id::N(n) => write!(f, "{n}"),
        }
    }
}
impl Default for Id {
    fn default() -> Self {
        Id::S(String::new())
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Employee {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub forename: String,
    #[serde(default)]
    pub surname: String,
}
impl Employee {
    pub fn display(&self) -> String {
        let name = format!("{} {}", self.forename, self.surname);
        let name = name.trim().to_string();
        if self.title.is_empty() {
            name
        } else {
            format!("{} {}", self.title, name).trim().to_string()
        }
    }
}

/// Some endpoints return a person as a plain string, others as an object.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Setter {
    Name(String),
    Employee(Employee),
}
impl Setter {
    pub fn display(&self) -> String {
        match self {
            Setter::Name(s) => s.clone(),
            Setter::Employee(e) => e.display(),
        }
    }
}

// ---------- auth ----------

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct UserInfo {
    pub id: Id,
    #[serde(default)]
    pub forename: String,
    #[serde(default)]
    pub surname: String,
    #[serde(default)]
    pub username: Option<String>,
    #[serde(default, rename = "type")]
    pub kind: Option<serde_json::Value>,
}
impl UserInfo {
    pub fn display(&self) -> String {
        format!("{} {}", self.forename, self.surname).trim().to_string()
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct EstablishmentInfo {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub logo: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoginData {
    /// The session bearer token. The wire name is `authtoken`.
    #[serde(alias = "auth_token")]
    pub authtoken: String,
    pub user: UserInfo,
    #[serde(default)]
    pub children: Vec<UserInfo>,
    #[serde(default)]
    pub establishment: Option<EstablishmentInfo>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct LoginContextData {
    #[serde(default)]
    pub establishment: Option<EstablishmentInfo>,
    #[serde(default)]
    pub user: Option<UserInfo>,
}

// ---------- status ----------

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct StatusLesson {
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub room: Option<Room>,
    #[serde(default)]
    pub start_time: Option<String>,
    #[serde(default)]
    pub end_time: Option<String>,
    #[serde(default)]
    pub period_name: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct StatusLessons {
    #[serde(default)]
    pub current: Option<StatusLesson>,
    #[serde(default)]
    pub next: Option<StatusLesson>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct StatusData {
    #[serde(default)]
    pub new_forms: Option<i64>,
    #[serde(default)]
    pub lessons: Option<StatusLessons>,
}

// ---------- homework ----------

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Attachment {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default, rename = "type")]
    pub kind: Option<String>,
    #[serde(default)]
    pub filename: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct HomeworkItem {
    pub id: Id,
    #[serde(default)]
    pub activity: String,
    #[serde(default)]
    pub subject: String,
    #[serde(default)]
    pub due_date: String,
    #[serde(default)]
    pub available_date: Option<String>,
    #[serde(default)]
    pub completed: bool,
    #[serde(default)]
    pub status: Option<String>,
    #[serde(default)]
    pub set_by: Option<Setter>,
    #[serde(default)]
    pub attachments: Vec<Attachment>,
    #[serde(default)]
    pub icon: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct HomeworkLists {
    #[serde(default)]
    pub current: Vec<HomeworkItem>,
    #[serde(default)]
    pub past: Vec<HomeworkItem>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct HomeworkData {
    #[serde(default)]
    pub homework: HomeworkLists,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct HomeworkDetailInner {
    pub id: Id,
    #[serde(default)]
    pub activity: String,
    #[serde(default)]
    pub subject: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub due_date: String,
    #[serde(default)]
    pub available_date: Option<String>,
    #[serde(default)]
    pub set_by: Option<Setter>,
    #[serde(default)]
    pub attachments: Vec<Attachment>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct HomeworkDetail {
    #[serde(default)]
    pub homework: HomeworkDetailInner,
}

// ---------- timetable ----------

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Room {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub moved: Option<bool>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct TeachingGroup {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub subject: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Lesson {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub period_id: Option<Id>,
    #[serde(default)]
    pub room: Option<Room>,
    #[serde(default)]
    pub teacher: Option<Setter>,
    #[serde(default)]
    pub teachers: Option<Setter>,
    #[serde(default)]
    pub teaching_group: Option<TeachingGroup>,
    #[serde(default)]
    pub subject: Option<String>,
    #[serde(default)]
    pub start_time: Option<String>,
    #[serde(default)]
    pub end_time: Option<String>,
    #[serde(default)]
    pub moved_from: Option<serde_json::Value>,
}
impl Lesson {
    pub fn teacher_name(&self) -> String {
        self.teacher
            .as_ref()
            .or(self.teachers.as_ref())
            .map(Setter::display)
            .unwrap_or_default()
    }
    pub fn room_name(&self) -> String {
        self.room
            .as_ref()
            .and_then(|r| r.name.clone())
            .unwrap_or_default()
    }
    pub fn subject_name(&self) -> String {
        self.subject
            .clone()
            .or_else(|| {
                self.teaching_group
                    .as_ref()
                    .and_then(|g| g.subject.clone())
            })
            .unwrap_or_default()
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Period {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub start_time: Option<String>,
    #[serde(default)]
    pub end_time: Option<String>,
    #[serde(default)]
    pub empty: Option<bool>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Day {
    pub date: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub lessons: Vec<Lesson>,
    #[serde(default)]
    pub periods: Vec<Period>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Week {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub days: Vec<Day>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct TimetableData {
    #[serde(default)]
    pub weeks: Vec<Week>,
}

// ---------- achievement / behaviour (points) ----------

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Recorded {
    #[serde(default)]
    pub employee_id: Option<Id>,
    #[serde(default)]
    pub date: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AchievementEvent {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub type_ids: Option<Vec<Id>>,
    #[serde(default)]
    pub date: String,
    #[serde(default)]
    pub comments: Option<String>,
    #[serde(default)]
    pub points: i64,
    #[serde(default)]
    pub recorded: Option<Recorded>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AchievementData {
    #[serde(default)]
    pub achievement: Vec<AchievementEvent>,
    #[serde(default)]
    pub employees: Vec<Employee>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct BehaviourEvent {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub type_ids: Option<Vec<Id>>,
    #[serde(default)]
    pub date: String,
    #[serde(default)]
    pub comments: Option<String>,
    #[serde(default)]
    pub points: i64,
    #[serde(default)]
    pub lesson_information: Option<String>,
    #[serde(default)]
    pub status_id: Option<Id>,
    #[serde(default)]
    pub recorded: Option<Recorded>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Detention {
    #[serde(default)]
    pub id: Option<Id>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub date: Option<String>,
    #[serde(default)]
    pub start_time: Option<String>,
    #[serde(default)]
    pub end_time: Option<String>,
    #[serde(default)]
    pub location: Option<String>,
    #[serde(default)]
    pub attended: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct BehaviourData {
    #[serde(default)]
    pub behaviour: Vec<BehaviourEvent>,
    #[serde(default)]
    pub detentions: Vec<Detention>,
    #[serde(default)]
    pub employees: Vec<Employee>,
}

/// Computed view for `edulink points` and the TUI Points tab.
#[derive(Debug, Clone, Default)]
pub struct PointsSummary {
    pub achievement_points: i64,
    pub behaviour_points: i64,
    pub achievement_events: Vec<AchievementEvent>,
    pub behaviour_events: Vec<BehaviourEvent>,
    pub detentions: Vec<Detention>,
    pub employees: Vec<Employee>,
}
impl PointsSummary {
    pub fn build(a: &AchievementData, b: &BehaviourData) -> Self {
        let mut employees = a.employees.clone();
        employees.extend(b.employees.iter().cloned());
        PointsSummary {
            achievement_points: a.achievement.iter().map(|e| e.points).sum(),
            behaviour_points: b.behaviour.iter().map(|e| e.points).sum(),
            achievement_events: a.achievement.clone(),
            behaviour_events: b.behaviour.clone(),
            detentions: b.detentions.clone(),
            employees,
        }
    }
    pub fn employee_name(&self, id: &Option<Id>) -> String {
        id.as_ref()
            .and_then(|want| {
                self.employees
                    .iter()
                    .find(|e| e.id.as_ref() == Some(want))
                    .map(Employee::display)
            })
            .unwrap_or_default()
    }
}
