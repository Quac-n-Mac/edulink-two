//! Transports: HTTP (live, read-only) and Mock (fixtures).
//! Every call is a JSON-RPC 2.0 POST to /api/?method=<Method> with an
//! X-API-Method header and a Bearer token once logged in.

use anyhow::{anyhow, bail, Context, Result};
use serde_json::{json, Value};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::RwLock;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub trait Transport: Send + Sync {
    /// Call a method and return the `result` object. Fails on RPC error
    /// or `success: false`. Boxed + Send so refreshes can run in spawned
    /// tasks behind a live UI.
    fn call<'a>(
        &'a self,
        method: &'a str,
        params: Value,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Value>> + Send + 'a>>;
    /// Store the session token after login. No-op for mocks.
    fn set_token(&self, _token: &str) {}
    /// Degraded (poor connectivity) mode: tight per-request timeouts.
    /// No-op for mocks.
    fn set_degraded(&self, _degraded: bool) {}
}

fn unwrap_result(v: &Value) -> Result<Value> {
    if let Some(err) = v.get("error") {
        bail!("json-rpc error: {err}");
    }
    let result = v
        .get("result")
        .cloned()
        .ok_or_else(|| anyhow!("response missing result: {v}"))?;
    if result.get("success") == Some(&Value::Bool(false)) {
        let msg = result
            .get("error")
            .map(|e| {
                e.as_str()
                    .map(str::to_string)
                    .unwrap_or_else(|| e.to_string())
            })
            .unwrap_or_else(|| "unknown api error".to_string());
        bail!("api error: {msg}");
    }
    Ok(result)
}

fn make_uuid() -> String {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0);
    let c = COUNTER.fetch_add(1, Ordering::Relaxed);
    format!(
        "{:08x}-{:04x}-4{:03x}-{:04x}-{:012x}",
        (nanos >> 32) as u32,
        (nanos >> 16) as u16,
        nanos as u16 & 0x0fff,
        (c as u16) | 0x8000,
        (nanos ^ (c << 20)) & 0xffffffffffff
    )
}

/// Live transport. Only read methods are ever sent; this client has no
/// write-method code paths.
pub struct HttpTransport {
    http: reqwest::Client,
    base: String,
    token: RwLock<Option<String>>,
    degraded: AtomicBool,
}

impl HttpTransport {
    pub fn new(host: &str) -> Result<Self> {
        let http = reqwest::Client::builder()
            .user_agent("EdulinkTwo/0.1 (read-only client)")
            .tcp_keepalive(Duration::from_secs(30))
            .pool_max_idle_per_host(4)
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(30))
            .build()?;
        Ok(Self {
            http,
            base: format!("https://{host}/api/"),
            token: RwLock::new(None),
            degraded: AtomicBool::new(false),
        })
    }
}

impl Transport for HttpTransport {
    fn call<'a>(
        &'a self,
        method: &'a str,
        params: Value,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Value>> + Send + 'a>> {
        Box::pin(self.call_inner(method, params))
    }

    fn set_token(&self, token: &str) {
        *self.token.write().unwrap() = Some(token.to_string());
    }

    fn set_degraded(&self, degraded: bool) {
        self.degraded.store(degraded, Ordering::Relaxed);
    }
}

impl HttpTransport {
    async fn call_inner(&self, method: &str, params: Value) -> Result<Value> {
        let url = format!("{}?method={method}", self.base);
        let body = json!({
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
            "uuid": make_uuid(),
            "id": "1",
        });
        let degraded = self.degraded.load(Ordering::Relaxed);
        let mut req = self
            .http
            .post(&url)
            .header("X-API-Method", method)
            .timeout(if degraded {
                Duration::from_secs(8)
            } else {
                Duration::from_secs(30)
            })
            .json(&body);
        if let Some(t) = self.token.read().unwrap().as_ref() {
            req = req.header("Authorization", format!("Bearer {t}"));
        }
        let resp = req.send().await.context("http send")?;
        let status = resp.status();
        if status == reqwest::StatusCode::UNAUTHORIZED {
            bail!("session expired (401) - log in again");
        }
        let v: Value = resp.error_for_status()?.json().await.context("parse json")?;
        unwrap_result(&v)
    }

}

/// Fixture-backed transport for tests and `--mock`. Maps a method to
/// `<dir>/<last-segment lowercase>.json` containing a full envelope.
pub struct MockTransport {
    dir: PathBuf,
}

impl MockTransport {
    pub fn new(dir: impl Into<PathBuf>) -> Self {
        Self { dir: dir.into() }
    }
}

impl Transport for MockTransport {
    fn call<'a>(
        &'a self,
        method: &'a str,
        _params: Value,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Value>> + Send + 'a>> {
        Box::pin(async move {
            let name = method.rsplit('.').next().unwrap_or(method).to_lowercase();
            let path = self.dir.join(format!("{name}.json"));
            let text = tokio::fs::read_to_string(&path)
                .await
                .with_context(|| format!("no fixture for {method} at {}", path.display()))?;
            let v: Value = serde_json::from_str(&text).context("fixture json")?;
            unwrap_result(&v)
        })
    }
}
