//! Offline demo mode for the DEMO button: invented fixtures loaded into
//! the cache under a fictional account, so the full app can be driven
//! (and screenshot-tested on emulators) without any real login.
//! Every name, school, record and id below is fictional by construction.

use crate::cache::Cache;
use crate::session::{AccountStore, Session};
use anyhow::Result;
use chrono::{Duration, Local};
use serde_json::{json, Value};
use std::path::PathBuf;

pub const DEMO_HOST: &str = "demo.offline";

fn day(off: i64) -> String {
    (Local::now() + Duration::days(off)).format("%Y-%m-%d").to_string()
}

fn weekday() -> String {
    Local::now().format("%A").to_string()
}

fn teacher(title: &str, forename: &str, surname: &str) -> Value {
    json!({"employee": {"id": "1", "title": title, "forename": forename, "surname": surname}})
}

fn homework() -> Value {
    json!({"homework": {
        "current": [
            {"id": "900001", "activity": "Fractions practice sheet", "subject": "Maths",
             "due_date": day(2), "available_date": day(-1), "completed": false, "source": "EduLink",
             "set_by": teacher("Mrs", "Elena", "Whitfield")},
            {"id": "900002", "activity": "Coastal landforms essay", "subject": "Geography",
             "due_date": day(0), "available_date": day(-3), "completed": false, "source": "EduLink",
             "set_by": teacher("Mr", "Owain", "Prichard")},
            {"id": "900003", "activity": "Read chapter 4 of the set text", "subject": "English",
             "due_date": day(1), "available_date": day(-2), "completed": false, "source": "MicrosoftTeams",
             "set_by": teacher("Ms", "Ruth", "Calloway")},
            {"id": "900004", "activity": "Periodic table quiz prep", "subject": "Chemistry",
             "due_date": day(4), "available_date": day(-1), "completed": true, "source": "EduLink",
             "set_by": teacher("Dr", "Imran", "Aziz")}
        ],
        "past": [
            {"id": "899901", "activity": "Algebra workbook page 12", "subject": "Maths",
             "due_date": day(-3), "completed": true, "source": "EduLink"},
            {"id": "899902", "activity": "Fieldwork sketch", "subject": "Geography",
             "due_date": day(-6), "completed": true, "source": "EduLink"},
            {"id": "899903", "activity": "Spelling list 8", "subject": "English",
             "due_date": day(-9), "completed": false, "source": "EduLink"}
        ]
    }})
}

fn lesson(start: &str, end: &str, subject: &str, room: &str, t: Value) -> Value {
    json!({"id": "1", "start_time": start, "end_time": end, "subject": subject,
           "room": {"id": "1", "name": room}, "teacher": t})
}

fn timetable() -> Value {
    let today = day(0);
    json!({"weeks": [{"name": "Week A", "days": [
        {"date": today, "name": weekday(), "lessons": [
            lesson("08:40", "09:40", "Maths", "M12", teacher("Mrs", "Elena", "Whitfield")),
            lesson("09:40", "10:40", "English", "E7", teacher("Ms", "Ruth", "Calloway")),
            lesson("11:00", "12:00", "Chemistry", "Lab 3", teacher("Dr", "Imran", "Aziz")),
            lesson("12:40", "13:40", "Geography", "G4", teacher("Mr", "Owain", "Prichard")),
            lesson("13:40", "14:40", "PE", "Sports Hall", teacher("Mr", "Tom", "Bevan"))
        ]}
    ]}]})
}

fn achievement() -> Value {
    json!({"achievement": [
        {"id": "1", "date": day(-1), "comments": "Excellent class contribution", "points": 3},
        {"id": "2", "date": day(-4), "comments": "Homework of the week", "points": 5},
        {"id": "3", "date": day(-8), "comments": "Helping at open evening", "points": 2}
    ], "employees": []})
}

fn behaviour() -> Value {
    json!({"behaviour": [
        {"id": "1", "date": day(-5), "comments": "Late to lesson", "points": 1, "lesson_information": "Period 3"}
    ], "detentions": [], "employees": []})
}

fn attendance() -> Value {
    json!({
        "today": {"sessions": [
            {"session": "AM", "date": day(0), "minutes_late": 0, "mark": {"code": "/", "name": "Present", "type": "present"}},
            {"session": "PM", "date": day(0), "minutes_late": 0, "mark": {"code": "\\", "name": "Present", "type": "present"}}
        ]},
        "statutory": [
            {"month": "This year", "values": {"present": 148, "absent": 4, "unauthorised": 0, "late": 2}, "exceptions": [
                {"date": day(-12), "description": "Medical appointment", "type": "absent", "period": "AM"}
            ]}
        ],
        "lesson": [
            {"subject": "Maths", "values": {"present": 30, "absent": 1, "late": 0}},
            {"subject": "English", "values": {"present": 28, "absent": 0, "late": 1}},
            {"subject": "Chemistry", "values": {"present": 29, "absent": 1, "late": 0}}
        ]
    })
}

fn exams() -> Value {
    json!({
        "countdown": {"minutes_to_go": 43200, "exam": "Maths mock"},
        "timetable": [
            {"id": "1", "datetime": format!("{} 09:00", day(30)), "title": "Maths mock", "room": "Main Hall", "seat": "A14"}
        ],
        "entries": [],
        "results": []
    })
}

fn noticeboard() -> Value {
    json!({"noticeboard": [
        {"id": "", "name": "Year 9 parents evening - booking opens Monday", "filename": null},
        {"id": "", "name": "Sports day timetable published", "filename": null}
    ]})
}

fn calendar() -> Value {
    json!({"calendar": [
        {"start_date": day(7), "description": "Parents evening", "all_day_event": false},
        {"start_date": day(21), "description": "End of term", "all_day_event": true}
    ]})
}

fn clubs() -> Value {
    json!({"clubs": [
        {"id": "1", "name": "Chess club", "description": "Tuesdays at lunch in M12. All abilities welcome."},
        {"id": "2", "name": "Coding club", "description": "Thursdays after school in IT2."}
    ]})
}

fn personal() -> Value {
    json!({"personal": {
        "forename": "Demo",
        "surname": "Learner",
        "date_of_birth": "2012-03-14",
        "gender": "Not disclosed",
        "year_group": "Year 9",
        "form_group": {"name": "9A", "employee": {"title": "Mrs", "forename": "Elena", "surname": "Whitfield"}}
    }})
}

fn inbox() -> Value {
    json!({"messages": []})
}

/// A generic homework detail for demo taps; the id is ignored.
fn hw_detail() -> Value {
    json!({"homework": {
        "id": "900001",
        "activity": "Fractions practice sheet",
        "subject": "Maths",
        "due_date": day(2),
        "available_date": day(-1),
        "description": "<p>Complete questions 1-20 on the fractions practice sheet.</p><p>Show your working for the last five questions. Hand in at the start of the next lesson.</p>",
        "set_by": teacher("Mrs", "Elena", "Whitfield"),
        "attachments": []
    }})
}

pub fn demo_session() -> Session {
    Session {
        host: DEMO_HOST.into(),
        authtoken: "demo".into(),
        username: "demo_learner".into(),
        user_id: "1".into(),
        learner_id: "1".into(),
        display_name: "Demo Learner".into(),
        school: "Ashfield Demo School".into(),
        school_code: "DEMO".into(),
        school_id: 0,
        saved_at: chrono::Utc::now(),
    }
}

pub fn is_demo(session: &Session) -> bool {
    session.host == DEMO_HOST
}

/// Create (or refresh) the demo account and fill every cached surface
/// with the invented fixtures. No network is touched.
pub async fn demo_login(state_dir: PathBuf, cache_dir: PathBuf) -> Result<Value> {
    let session = demo_session();
    let mut store = AccountStore::load(&state_dir)?;
    store.upsert(session.clone());
    store.save(&state_dir)?;
    let cache = Cache::new(&cache_dir)?.for_user(&session.key())?;
    cache.write("homework", &homework()).await?;
    cache.write("timetable", &timetable()).await?;
    cache.write("achievement", &achievement()).await?;
    cache.write("behaviour", &behaviour()).await?;
    cache.write("attendance", &attendance()).await?;
    cache.write("exams", &exams()).await?;
    cache.write("noticeboard", &noticeboard()).await?;
    cache.write("calendar", &calendar()).await?;
    cache.write("clubs", &clubs()).await?;
    cache.write("personal", &personal()).await?;
    cache.write("inbox", &inbox()).await?;
    cache.write("hwdetail-900001", &hw_detail()).await?;
    cache.write("hwdetail-900002", &hw_detail()).await?;
    cache.write("hwdetail-900003", &hw_detail()).await?;
    cache.write("hwdetail-900004", &hw_detail()).await?;
    Ok(json!({
        "ok": true,
        "key": session.key(),
        "display_name": session.display_name,
        "school": session.school,
    }))
}
