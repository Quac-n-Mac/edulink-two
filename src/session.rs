//! Session persistence: host + bearer token + identity, 0600 on unix.
//! The directory comes from the caller (desktop config dir, Android filesDir).

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Session {
    pub host: String,
    pub authtoken: String,
    pub username: String,
    pub user_id: String,
    pub learner_id: String,
    pub display_name: String,
    pub school: String,
    pub saved_at: chrono::DateTime<chrono::Utc>,
}

impl Session {
    pub fn path(dir: &Path) -> PathBuf {
        dir.join("session.json")
    }

    pub fn load(dir: &Path) -> Result<Option<Session>> {
        let path = Self::path(dir);
        if !path.exists() {
            return Ok(None);
        }
        let text = std::fs::read_to_string(&path).context("read session")?;
        Ok(Some(serde_json::from_str(&text).context("parse session")?))
    }

    pub fn save(&self, dir: &Path) -> Result<()> {
        std::fs::create_dir_all(dir)?;
        let path = Self::path(dir);
        std::fs::write(&path, serde_json::to_string_pretty(self)?)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))?;
        }
        Ok(())
    }

    pub fn clear(dir: &Path) -> Result<()> {
        let path = Self::path(dir);
        if path.exists() {
            std::fs::remove_file(&path)?;
        }
        Ok(())
    }

    /// Stable identity of an account: username@host.
    pub fn key(&self) -> String {
        format!("{}@{}", self.username, self.host)
    }
}

/// Multi-account store (parity with stock Edulink): every login is
/// saved, each keeps its own token, switching is instant.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AccountStore {
    #[serde(default)]
    pub current: Option<String>,
    #[serde(default)]
    pub accounts: Vec<Session>,
}

impl AccountStore {
    pub fn path(dir: &Path) -> PathBuf {
        dir.join("accounts.json")
    }

    pub fn load(dir: &Path) -> Result<AccountStore> {
        let path = Self::path(dir);
        if !path.exists() {
            return Ok(AccountStore::default());
        }
        let text = std::fs::read_to_string(&path).context("read accounts")?;
        Ok(serde_json::from_str(&text).context("parse accounts")?)
    }

    pub fn save(&self, dir: &Path) -> Result<()> {
        std::fs::create_dir_all(dir)?;
        let path = Self::path(dir);
        std::fs::write(&path, serde_json::to_string_pretty(self)?)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))?;
        }
        Ok(())
    }

    /// Insert or replace an account and make it current.
    pub fn upsert(&mut self, session: Session) {
        let key = session.key();
        self.accounts.retain(|s| s.key() != key);
        self.accounts.push(session);
        self.current = Some(key);
    }

    pub fn current(&self) -> Option<&Session> {
        let cur = self.current.as_ref()?;
        self.accounts.iter().find(|s| &s.key() == cur)
    }

    pub fn set_current(&mut self, key_or_name: &str) -> Result<()> {
        let found = self
            .accounts
            .iter()
            .find(|s| s.key() == key_or_name || s.username == key_or_name)
            .map(|s| s.key());
        match found {
            Some(k) => {
                self.current = Some(k);
                Ok(())
            }
            None => anyhow::bail!("no saved account '{key_or_name}'"),
        }
    }

    pub fn remove(&mut self, key_or_name: &str) -> bool {
        let before = self.accounts.len();
        self.accounts
            .retain(|s| s.key() != key_or_name && s.username != key_or_name);
        let removed = self.accounts.len() != before;
        if removed {
            if self.current.as_deref() == Some(key_or_name)
                || self
                    .current
                    .as_ref()
                    .map(|c| self.accounts.iter().all(|s| &s.key() != c))
                    .unwrap_or(true)
            {
                self.current = self.accounts.first().map(|s| s.key());
            }
        }
        removed
    }
}
