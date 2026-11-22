use edulink_two::{Client, MockTransport};
use edulink_two::transport::Transport;
use serde_json::{json, Value};
use std::{path::PathBuf, sync::{Arc, Mutex}};

struct RotatingTransport {
    fixtures: MockTransport,
    token: Arc<Mutex<String>>,
}
impl Transport for RotatingTransport {
    fn set_token(&self, token: &str) { *self.token.lock().unwrap() = token.into(); }
    fn call<'a>(&'a self, method: &'a str, params: Value) -> std::pin::Pin<Box<dyn std::future::Future<Output=anyhow::Result<Value>> + Send + 'a>> {
        Box::pin(async move {
            if method == "EduLink.LoginContext" {
                let mut response = self.fixtures.call(method, params).await?;
                response["authtoken"] = json!("rotated-fixture-token");
                return Ok(response);
            }
            if method != "EduLink.Login" && method != "EduLink.Status" {
                anyhow::ensure!(*self.token.lock().unwrap() == "rotated-fixture-token", "old token used after handshake");
            }
            self.fixtures.call(method, params).await
        })
    }
}
#[tokio::test]
async fn context_token_is_used_for_prefetch_and_returned_for_persistence() {
    let token = Arc::new(Mutex::new(String::new()));
    let transport = RotatingTransport { fixtures: MockTransport::new(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("fixtures")), token: token.clone() };
    let client = Client::new(transport, "mock.local", "fixture-user");
    let (login, prefetch) = client.login("fixture-password", 81, None, "2026-11-23").await.unwrap();
    assert_eq!(login.authtoken, "rotated-fixture-token");
    assert_eq!(*token.lock().unwrap(), "rotated-fixture-token");
    assert!(prefetch.homework.result.is_ok());
    assert!(prefetch.timetable.result.is_ok());
    assert!(prefetch.attendance.result.is_ok());
    assert!(prefetch.clubs.result.is_ok());
}
