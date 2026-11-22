//! End-to-end against the fixture MockTransport: login, every surface,
//! detail fetch, and a cache roundtrip. No network.

use edulink_two::cache::Cache;
use edulink_two::{Client, MockTransport};
use std::path::PathBuf;

fn fixtures() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("fixtures")
}

#[tokio::test]
async fn login_prefetches_everything() {
    let client = Client::new(MockTransport::new(fixtures()), "mock.local", "tstudent42");
    let (login, pre) = client
        .login("fixture-password", 81, None, "2026-11-23")
        .await
        .expect("login");
    assert_eq!(login.user.display(), "Test Student");
    assert!(pre.homework.result.is_ok());
    assert!(pre.timetable.result.is_ok());
    assert!(pre.achievement.result.is_ok());
    assert!(pre.behaviour.result.is_ok());

    let hw = pre.homework.result.unwrap();
    assert_eq!(hw.homework.current.len(), 3);
    assert_eq!(hw.homework.past.len(), 2);
    assert_eq!(hw.homework.current[1].set_by.as_ref().unwrap().display(), "Mrs B Sample");

    let tb = pre.timetable.result.unwrap();
    assert_eq!(tb.weeks[0].days.len(), 5);
    assert_eq!(tb.weeks[0].days[0].lessons.len(), 5);
    assert_eq!(tb.weeks[0].days[0].lessons[0].subject_name(), "Science");
    assert_eq!(tb.weeks[0].days[0].lessons[0].room_name(), "Lab 2");

    let ach = pre.achievement.result.unwrap();
    let beh = pre.behaviour.result.unwrap();
    let points = edulink_two::models::PointsSummary::build(&ach, &beh);
    assert_eq!(points.achievement_points, 20);
    assert_eq!(points.behaviour_points, 3);
    assert_eq!(points.detentions.len(), 1);
    assert_eq!(points.employee_name(&ach.achievement[0].recorded.as_ref().and_then(|r| r.employee_id.clone())), "Mr A Example");
}

#[tokio::test]
async fn homework_details_expand() {
    let client = Client::new(MockTransport::new(fixtures()), "mock.local", "tstudent42");
    let d = client.homework_details("900001", "homework").await.expect("details");
    assert!(d.homework.description.contains("cell wall"));
    assert_eq!(d.homework.attachments.len(), 1);
}

#[tokio::test]
async fn cache_roundtrip_and_staleness() {
    let dir = std::env::temp_dir().join(format!("edulink-two-test-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let cache = Cache::new(&dir).unwrap().for_user("fixture-user").unwrap();
    let client = Client::new(MockTransport::new(fixtures()), "mock.local", "tstudent42");
    let hw = client.homework().await.unwrap();
    cache.write("homework", &hw).await.unwrap();
    let back: edulink_two::Cached<edulink_two::models::HomeworkData> =
        cache.read("homework").await.unwrap();
    assert_eq!(back.data.homework.current.len(), 3);
    assert!(back.fresh_for(edulink_two::cache::TTL_HOMEWORK));
    let _ = std::fs::remove_dir_all(&dir);
}
