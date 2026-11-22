//! Edulink Two core library: models, transports, session, cache, client.
//! Read-only by construction: only read methods exist in this crate.

pub mod cache;
pub mod cli;
pub mod client;
pub mod models;
pub mod session;
pub mod transport;
pub mod tui;

pub use cache::{Cache, Cached};
pub use client::{Client, Prefetch};
pub use session::Session;
pub use transport::{HttpTransport, MockTransport, Transport};
