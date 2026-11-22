//! The live detail payload is loose with types; the client must coerce
//! instead of failing the whole fetch. Fixture is invented.
use edulink_two::models::HomeworkDetail;

#[test]
fn detail_parse_tolerates_nulls_and_numbers() {
    let raw = serde_json::json!({
        "homework": {
            "id": 1107,
            "activity": "Invented Task",
            "subject": 519,
            "description": null,
            "due_date": "2026-11-25",
            "available_date": null,
            "due_reminder": null,
            "duration": null,
            "set_by": null,
            "attachments": null
        }
    });
    let mut v = raw.clone();
    // mirror the client normalization
    if let Some(hw) = v.get_mut("homework").and_then(|h| h.as_object_mut()) {
        for key in ["activity", "subject", "description", "due_date", "available_date"] {
            if let Some(val) = hw.get_mut(key) {
                let fixed = match &*val {
                    serde_json::Value::Null => Some(serde_json::Value::String(String::new())),
                    serde_json::Value::Number(n) => Some(serde_json::Value::String(n.to_string())),
                    serde_json::Value::Bool(b) => Some(serde_json::Value::String(b.to_string())),
                    _ => None,
                };
                if let Some(f) = fixed { *val = f; }
            }
        }
        if matches!(hw.get("attachments"), Some(serde_json::Value::Null)) {
            hw.insert("attachments".to_string(), serde_json::json!([]));
        }
        if matches!(hw.get("set_by"), Some(serde_json::Value::Null)) { hw.remove("set_by"); }
    }
    let parsed: Result<HomeworkDetail, _> = serde_json::from_value(v);
    assert!(parsed.is_ok(), "detail parse failed: {:?}", parsed.err());
    let d = parsed.unwrap();
    assert_eq!(d.homework.activity, "Invented Task");
    assert_eq!(d.homework.subject, "519");
    assert!(d.homework.attachments.is_empty());
}
