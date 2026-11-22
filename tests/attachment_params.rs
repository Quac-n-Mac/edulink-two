use edulink_two::Client;
use edulink_two::transport::Transport;
use serde_json::{json, Value};
struct AttachmentTransport;
impl Transport for AttachmentTransport {
    fn call<'a>(&'a self, method: &'a str, params: Value) -> std::pin::Pin<Box<dyn std::future::Future<Output=anyhow::Result<Value>> + Send + 'a>> {
        Box::pin(async move {
            anyhow::ensure!(method == "EduLink.HomeworkAttachmentFetch", "wrong method");
            anyhow::ensure!(params["homework_id"] == "fixture-homework", "missing homework ID");
            anyhow::ensure!(params["attachment_id"] == "fixture-file", "missing attachment ID");
            Ok(json!({"attachment":{"filename":"fixture.pdf","data":"JVBERg=="}}))
        })
    }
}
#[tokio::test]
async fn download_sends_homework_and_attachment_ids() {
    let client = Client::new(AttachmentTransport, "mock.local", "fixture-user");
    let result = client.homework_attachment("fixture-homework", "fixture-file").await.unwrap();
    assert_eq!(result["filename"], "fixture.pdf");
}
