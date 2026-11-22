//! Instant-render cache: one JSON file per surface, timestamped.
//! The UI renders the cache first and refreshes in parallel behind it.

use anyhow::{Context, Result};
use chrono::{DateTime, Duration, Utc};
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Cached<T> {
    pub fetched_at: DateTime<Utc>,
    pub data: T,
}

impl<T> Cached<T> {
    pub fn age(&self) -> Duration {
        Utc::now().signed_duration_since(self.fetched_at)
    }
    pub fn fresh_for(&self, ttl: Duration) -> bool {
        self.age() < ttl
    }
}

pub const TTL_HOMEWORK: Duration = Duration::minutes(5);
pub const TTL_POINTS: Duration = Duration::minutes(5);
pub const TTL_TIMETABLE: Duration = Duration::hours(6);
pub const TTL_HW_DETAIL: Duration = Duration::hours(24);

#[derive(Debug, Clone)]
pub struct Cache {
    dir: PathBuf,
}

impl Cache {
    pub fn new(dir: impl Into<PathBuf>) -> Result<Self> {
        let dir = dir.into();
        std::fs::create_dir_all(&dir).context("create cache dir")?;
        Ok(Self { dir })
    }

    pub fn for_user(&self, user: &str) -> Result<Cache> {
        let safe: String = user
            .chars()
            .map(|c| if c.is_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
            .collect();
        Cache::new(self.dir.join(safe))
    }

    fn path(&self, name: &str) -> PathBuf {
        self.dir.join(format!("{name}.json"))
    }

    pub async fn read<T: DeserializeOwned>(&self, name: &str) -> Result<Cached<T>> {
        let text = tokio::fs::read_to_string(self.path(name))
            .await
            .with_context(|| format!("cache miss: {name}"))?;
        serde_json::from_str(&text).context("cache parse")
    }

    pub fn read_sync<T: DeserializeOwned>(&self, name: &str) -> Result<Cached<T>> {
        let text = std::fs::read_to_string(self.path(name))
            .with_context(|| format!("cache miss: {name}"))?;
        serde_json::from_str(&text).context("cache parse")
    }

    pub async fn write<T: Serialize>(&self, name: &str, data: &T) -> Result<()> {
        let cached = Cached {
            fetched_at: Utc::now(),
            data,
        };
        let tmp = self.path(name).with_extension("json.tmp");
        tokio::fs::write(&tmp, serde_json::to_string(&cached)?).await?;
        tokio::fs::rename(&tmp, self.path(name)).await?;
        Ok(())
    }
}
