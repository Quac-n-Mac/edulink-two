//! CLI: `edulink login|logout|sync|hw|hw+|tb|points|status|tui`
//! Offline-first: every command renders the last-known cache instantly
//! and only touches the network to refresh.

use crate::cache::{Cache, Cached, TTL_HOMEWORK, TTL_HW_DETAIL, TTL_POINTS, TTL_TIMETABLE};
const TTL_SLOW: Duration = Duration::hours(12);
use crate::client::Client;
use crate::models::*;
use crate::session::{AccountStore, Session};
use crate::transport::{HttpTransport, MockTransport, Transport};
use anyhow::{anyhow, bail, Context, Result};
use chrono::{Duration, Local, Utc};
use clap::{Parser, Subcommand};
use serde::de::DeserializeOwned;
use serde::Serialize;
use std::future::Future;
use std::path::PathBuf;
use std::time::Instant;

#[derive(Parser)]
#[command(
    name = "edulink",
    version,
    about = "Edulink Two - fast, offline-first, read-only Edulink One client"
)]
pub struct Args {
    /// Use fixture directory instead of the network (testing).
    #[arg(long, global = true)]
    pub mock: Option<PathBuf>,
    /// Output raw JSON.
    #[arg(long, global = true)]
    pub json: bool,
    /// Force a network refresh even if the cache is fresh.
    #[arg(long, global = true)]
    pub refresh: bool,
    /// Never touch the network; show last-known data only.
    #[arg(long, global = true)]
    pub offline: bool,
    /// Simulate poor connectivity: tight timeouts, minimal requests.
    #[arg(long, global = true)]
    pub poor: bool,
    #[command(subcommand)]
    pub cmd: Cmd,
}

#[derive(Subcommand)]
pub enum Cmd {
    /// Log in and pre-cache every surface in parallel.
    Login {
        username: String,
        /// School ID or postcode (resolved like the stock app).
        #[arg(long)]
        code: Option<String>,
        /// API host, e.g. www9.edulinkone.com (skip --code resolution).
        #[arg(long)]
        host: Option<String>,
        /// Establishment id when using --host directly.
        #[arg(long)]
        establishment: Option<i64>,
        /// Learner id for timetable/points (defaults to the login user id).
        #[arg(long)]
        learner_id: Option<String>,
    },
    /// Remove the current account (cache is kept).
    Logout {
        /// Remove a specific saved account instead of the current one.
        username: Option<String>,
    },
    /// List saved accounts.
    Accounts,
    /// Switch the active account: `edulink switch <username>`.
    Switch { username: String },
    /// Refresh every surface in parallel now.
    Sync,
    /// Homework list.
    Hw,
    /// Expand one homework's full description: `edulink hw+ <id>`.
    #[command(name = "hw+")]
    HwPlus { id: Option<String> },
    /// Timetable (default: today).
    Tb { date: Option<String> },
    /// Achievement + behaviour points.
    Points,
    /// Current/next lesson.
    Status,
    /// Attendance (today, statutory %, per-lesson).
    Att,
    /// Exams (countdown, timetable, entries, results).
    Exams,
    /// School noticeboard.
    Notices,
    /// School calendar.
    Cal { date: Option<String> },
    /// Clubs.
    Clubs,
    /// Profile / personal details.
    Me,
    /// Messages (read-only inbox listing; never marks anything read).
    Msg,
    /// Full-screen terminal UI.
    Tui,
}

const DEFAULT_HOST: &str = "www9.edulinkone.com";

enum Source {
    FreshCache(std::time::Duration),
    StaleCache(std::time::Duration),
    Live(u128),
}

fn age_str(d: std::time::Duration) -> String {
    let s = d.as_secs() as i64;
    if s < 90 {
        format!("{s}s ago")
    } else if s < 5400 {
        format!("{}m ago", s / 60)
    } else if s < 172800 {
        format!("{}h ago", s / 3600)
    } else {
        format!("{}d ago", s / 86400)
    }
}

fn footnote(src: &Source) -> String {
    match src {
        Source::FreshCache(age) => format!("(cache · {})", age_str(*age)),
        Source::StaleCache(age) => format!("(STALE cache · {} · refresh failed/offline)", age_str(*age)),
        Source::Live(ms) => format!("(live · {ms}ms)"),
    }
}

fn dirs() -> Result<(PathBuf, PathBuf)> {
    let proj = directories::ProjectDirs::from("dev", "EdulinkTwo", "edulink-two")
        .ok_or_else(|| anyhow!("no config dir"))?;
    Ok((
        proj.config_dir().to_path_buf(),
        proj.cache_dir().to_path_buf(),
    ))
}

/// Cache-first surface fetch: fresh cache wins instantly; otherwise a
/// live fetch, falling back to stale cache on any failure.
async fn surface<T, F, Fut>(
    cache: &Cache,
    name: &str,
    ttl: Duration,
    refresh: bool,
    offline: bool,
    fetch: F,
) -> Result<(Cached<T>, Source)>
where
    T: Serialize + DeserializeOwned,
    F: FnOnce() -> Fut,
    Fut: Future<Output = Result<T>>,
{
    let cached: Option<Cached<T>> = cache.read(name).await.ok();
    if !refresh {
        if let Some(c) = &cached {
            if c.fresh_for(ttl) {
                let age = c.age().to_std().unwrap_or_default();
                return Ok((cache.read(name).await?, Source::FreshCache(age)));
            }
        }
    }
    if offline {
        return match cached {
            Some(c) => {
                let age = c.age().to_std().unwrap_or_default();
                Ok((c, Source::StaleCache(age)))
            }
            None => bail!("offline and nothing cached yet for {name}"),
        };
    }
    let t = Instant::now();
    match fetch().await {
        Ok(data) => {
            let ms = t.elapsed().as_millis();
            cache.write(name, &data).await?;
            Ok((
                Cached {
                    fetched_at: Utc::now(),
                    data,
                },
                Source::Live(ms),
            ))
        }
        Err(e) => match cached {
            Some(c) => {
                eprintln!("# refresh failed ({e:#}); showing last-known data");
                let age = c.age().to_std().unwrap_or_default();
                Ok((c, Source::StaleCache(age)))
            }
            None => Err(e).context("no cache to fall back on"),
        },
    }
}

fn print_json<T: Serialize>(t: &T) -> Result<()> {
    println!("{}", serde_json::to_string_pretty(t)?);
    Ok(())
}

fn strip_html(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut in_tag = false;
    for c in s.chars() {
        match c {
            '<' => in_tag = true,
            '>' => in_tag = false,
            _ if !in_tag => out.push(c),
            _ => {}
        }
    }
    out.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn render_homework(hw: &HomeworkData) {
    if hw.homework.current.is_empty() {
        println!("No current homework.");
    } else {
        println!("CURRENT HOMEWORK");
        println!("{:<10}  {:<12}  {:<44}  {}", "DUE", "SUBJECT", "ACTIVITY", "STATUS");
        for h in &hw.homework.current {
            let status = h
                .status
                .clone()
                .unwrap_or_else(|| if h.completed { "Done".into() } else { "".into() });
            println!(
                "{:<10}  {:<12}  {:<44}  {}",
                h.due_date,
                truncate(&h.subject, 12),
                truncate(&h.activity, 44),
                status
            );
            if !h.attachments.is_empty() {
                for a in &h.attachments {
                    println!(
                        "{:<10}  {:<12}  [attachment: {} - not downloaded]",
                        "",
                        "",
                        a.filename.clone().unwrap_or_default()
                    );
                }
            }
        }
    }
    if !hw.homework.past.is_empty() {
        println!();
        println!("PAST ({} items)", hw.homework.past.len());
        for h in hw.homework.past.iter().take(10) {
            println!(
                "{:<10}  {:<12}  {}",
                h.due_date,
                truncate(&h.subject, 12),
                truncate(&h.activity, 44)
            );
        }
    }
}

fn render_detail(d: &HomeworkDetail) {
    let h = &d.homework;
    println!("{}", h.activity);
    println!("Subject: {}", h.subject);
    println!("Due:     {}", h.due_date);
    if let Some(s) = &h.set_by {
        println!("Set by:  {}", s.display());
    }
    println!();
    println!("{}", strip_html(&h.description));
    if !h.attachments.is_empty() {
        println!();
        println!("Attachments (listed only, never auto-downloaded):");
        for a in &h.attachments {
            println!("  - {}", a.filename.clone().unwrap_or_default());
        }
    }
}

fn render_timetable(tb: &TimetableData, date: &str) {
    let mut found = false;
    for week in &tb.weeks {
        for day in &week.days {
            if day.date == date {
                found = true;
                println!(
                    "{} - {}",
                    day.name.clone().unwrap_or_default(),
                    day.date
                );
                println!(
                    "{:<6}  {:<5}-{:<5}  {:<16}  {:<8}  {}",
                    "PER", "START", "END", "SUBJECT", "ROOM", "TEACHER"
                );
                for l in &day.lessons {
                    println!(
                        "{:<6}  {:<5}-{:<5}  {:<16}  {:<8}  {}",
                        l.period_id
                            .as_ref()
                            .map(|i| i.to_string())
                            .unwrap_or_default(),
                        l.start_time.clone().unwrap_or_default(),
                        l.end_time.clone().unwrap_or_default(),
                        truncate(&l.subject_name(), 16),
                        truncate(&l.room_name(), 8),
                        truncate(&l.teacher_name(), 20)
                    );
                }
            }
        }
    }
    if !found {
        println!("No lessons recorded for {date}.");
    }
}

fn render_points(a: &AchievementData, b: &BehaviourData) {
    let p = PointsSummary::build(a, b);
    println!("POINTS");
    println!("  Achievement: +{}", p.achievement_points);
    println!("  Behaviour:   -{} (raw {})", p.behaviour_points, p.behaviour_points);
    println!("  Net:         {}", p.achievement_points - p.behaviour_points);
    if !p.achievement_events.is_empty() {
        println!();
        println!("ACHIEVEMENTS");
        for e in p.achievement_events.iter().take(10) {
            println!(
                "  {:<10}  +{:<3}  {:<40}  {}",
                e.date,
                e.points,
                truncate(e.comments.as_deref().unwrap_or(""), 40),
                p.employee_name(&e.recorded.as_ref().and_then(|r| r.employee_id.clone()))
            );
        }
    }
    if !p.behaviour_events.is_empty() {
        println!();
        println!("BEHAVIOUR");
        for e in p.behaviour_events.iter().take(10) {
            println!(
                "  {:<10}  -{:<3}  {:<40}  {}",
                e.date,
                e.points,
                truncate(e.comments.as_deref().unwrap_or(""), 40),
                truncate(e.lesson_information.as_deref().unwrap_or(""), 20)
            );
        }
    }
    if !p.detentions.is_empty() {
        println!();
        println!("DETENTIONS");
        for d in &p.detentions {
            println!(
                "  {:<10}  {:<30}  {:<10}  {}",
                d.date.clone().unwrap_or_default(),
                truncate(d.description.as_deref().unwrap_or(""), 30),
                d.location.clone().unwrap_or_default(),
                d.attended.clone().unwrap_or_default()
            );
        }
    }
}

fn truncate(s: &str, n: usize) -> String {
    if s.chars().count() <= n {
        s.to_string()
    } else {
        format!("{}...", s.chars().take(n.saturating_sub(3)).collect::<String>())
    }
}

async fn run_with<T: Transport + 'static>(args: &Args, client: Client<T>, session: &Session, cache_root: &std::path::Path) -> Result<()> {
    match &args.cmd {
        Cmd::Login { .. } | Cmd::Logout { .. } | Cmd::Accounts | Cmd::Switch { .. } => unreachable!("handled by caller"),
        Cmd::Tui => {
            let cache = Cache::new(cache_root)?.for_user(&session.key())?;
            return crate::tui::run(client, cache, session.learner_id.clone(), args.offline).await;
        }
        _ => {}
    }
    let cache = Cache::new(cache_root)?.for_user(&session.key())?;
    let today = Local::now().format("%Y-%m-%d").to_string();
    let learner = session.learner_id.clone();

    match &args.cmd {
        Cmd::Sync => {
            if args.offline {
                println!("offline - nothing to sync");
                return Ok(());
            }
            let pre = client.prefetch_all(&learner, &today).await;
            let mut ok = 0;
            if let Ok(d) = pre.homework.result {
                cache.write("homework", &d).await?;
                ok += 1;
                println!("homework:   {:>5}ms", pre.homework.elapsed_ms);
            } else if let Err(e) = pre.homework.result {
                println!("homework:   FAILED ({e:#})");
            }
            if let Ok(d) = pre.timetable.result {
                cache.write("timetable", &d).await?;
                ok += 1;
                println!("timetable:  {:>5}ms", pre.timetable.elapsed_ms);
            } else if let Err(e) = pre.timetable.result {
                println!("timetable:  FAILED ({e:#})");
            }
            if let Ok(d) = pre.achievement.result {
                cache.write("achievement", &d).await?;
                ok += 1;
                println!("achievement:{:>5}ms", pre.achievement.elapsed_ms);
            } else if let Err(e) = pre.achievement.result {
                println!("achievement:FAILED ({e:#})");
            }
            if let Ok(d) = pre.behaviour.result {
                cache.write("behaviour", &d).await?;
                ok += 1;
                println!("behaviour:  {:>5}ms", pre.behaviour.elapsed_ms);
            } else if let Err(e) = pre.behaviour.result {
                println!("behaviour:  FAILED ({e:#})");
            }
            macro_rules! sync_surface {
                ($f:ident, $name:literal) => {
                    if let Ok(d) = pre.$f.result {
                        cache.write($name, &d).await?;
                        ok += 1;
                        println!("{:<12}{:>5}ms", $name, pre.$f.elapsed_ms);
                    } else if let Err(e) = pre.$f.result {
                        println!("{:<12}FAILED ({e:#})", $name);
                    }
                };
            }
            sync_surface!(attendance, "attendance");
            sync_surface!(exams, "exams");
            sync_surface!(noticeboard, "noticeboard");
            sync_surface!(calendar, "calendar");
            sync_surface!(clubs, "clubs");
            sync_surface!(personal, "personal");
            sync_surface!(inbox, "inbox");
            println!("synced {ok}/11 surfaces in {}ms (parallel)", pre.total_ms);
            Ok(())
        }
        Cmd::Hw => {
            let (hw, src) = surface(&cache, "homework", TTL_HOMEWORK, args.refresh, args.offline, || client.homework()).await?;
            if args.json {
                return print_json(&hw.data);
            }
            render_homework(&hw.data);
            println!();
            println!("{}", footnote(&src));
            Ok(())
        }
        Cmd::HwPlus { id } => {
            let id = match id {
                Some(i) => i.clone(),
                None => bail!("usage: edulink hw+ <id>  (ids shown by `edulink hw --json`)"),
            };
            let name = format!("hwdetail-{id}");
            let (d, src) = surface(&cache, &name, TTL_HW_DETAIL, args.refresh, args.offline, || client.homework_details(&id, "EduLink")).await?;
            if args.json {
                return print_json(&d.data);
            }
            render_detail(&d.data);
            println!();
            println!("{}", footnote(&src));
            Ok(())
        }
        Cmd::Tb { date } => {
            let date = date.clone().unwrap_or_else(|| today.clone());
            let (tb, src) = surface(&cache, "timetable", TTL_TIMETABLE, args.refresh, args.offline, || client.timetable(&learner, &date)).await?;
            if args.json {
                return print_json(&tb.data);
            }
            render_timetable(&tb.data, &date);
            println!();
            println!("{}", footnote(&src));
            Ok(())
        }
        Cmd::Points => {
            let (a, srca) = surface(&cache, "achievement", TTL_POINTS, args.refresh, args.offline, || client.achievement(&learner)).await?;
            let (b, srcb) = surface(&cache, "behaviour", TTL_POINTS, args.refresh, args.offline, || client.behaviour(&learner)).await?;
            if args.json {
                return print_json(&serde_json::json!({"achievement": a.data, "behaviour": b.data}));
            }
            render_points(&a.data, &b.data);
            println!();
            println!("achievement {} | behaviour {}", footnote(&srca), footnote(&srcb));
            Ok(())
        }
        Cmd::Status => {
            let (st, src) = surface(&cache, "status", Duration::seconds(60), args.refresh, args.offline, || client.status()).await?;
            if args.json {
                return print_json(&st.data);
            }
            if let Some(l) = &st.data.lessons {
                if let Some(c) = &l.current {
                    println!("Now:  {}", c.description.clone().unwrap_or_default());
                }
                if let Some(n) = &l.next {
                    println!("Next: {}", n.description.clone().unwrap_or_default());
                }
            } else {
                println!("No lesson info right now.");
            }
            println!();
            println!("{}", footnote(&src));
            Ok(())
        }
        Cmd::Att => {
            let (d, src) = surface(&cache, "attendance", TTL_SLOW, args.refresh, args.offline, || client.attendance(&learner)).await?;
            if args.json { return print_json(&d.data); }
            if let Some(t) = &d.data.today {
                println!("TODAY");
                for s in &t.sessions {
                    let m = s.mark.as_ref();
                    println!("  {}: {} ({})", s.session, m.map(|m| m.name.as_str()).unwrap_or("?"), if s.minutes_late > 0 { format!("{} min late", s.minutes_late) } else { "on time".into() });
                }
            }
            let tot_p: i64 = d.data.statutory.iter().map(|m| m.values.get("present").copied().unwrap_or(0)).sum();
            let tot_a: i64 = d.data.statutory.iter().map(|m| m.values.get("absent").copied().unwrap_or(0)).sum();
            let tot_u: i64 = d.data.statutory.iter().map(|m| m.values.get("unauthorised").copied().unwrap_or(0)).sum();
            let total = tot_p + tot_a + tot_u;
            if total > 0 {
                println!();
                println!("STATUTORY: {:.1}% present ({} present / {} sessions, {} absent, {} unauthorised)",
                    100.0 * tot_p as f64 / total as f64, tot_p, total, tot_a, tot_u);
            }
            for m in &d.data.statutory {
                for e in &m.exceptions {
                    println!("  exception: {} {} - {}", e.date, e.period, e.description);
                }
            }
            if !d.data.lesson.is_empty() {
                println!();
                println!("BY LESSON");
                for l in &d.data.lesson {
                    let p = l.values.get("present").copied().unwrap_or(0);
                    let a = l.values.get("absent").copied().unwrap_or(0) + l.values.get("unauthorised").copied().unwrap_or(0);
                    let lt = l.values.get("late").copied().unwrap_or(0);
                    println!("  {:<14} {} present, {} absent, {} late", l.subject, p, a, lt);
                }
            }
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Exams => {
            let (d, src) = surface(&cache, "exams", TTL_SLOW, args.refresh, args.offline, || client.exams(&learner)).await?;
            if args.json { return print_json(&d.data); }
            if let Some(c) = &d.data.countdown {
                let days = c.minutes_to_go / 1440;
                println!("NEXT EXAM: {} in {} days", c.exam, days);
                println!();
            }
            if !d.data.timetable.is_empty() {
                println!("EXAM TIMETABLE");
                for e in &d.data.timetable {
                    println!("  {:<16}  {:<8}  {:<28}  {}  {}", e.datetime, e.code, truncate(&e.title, 28), e.room.clone().unwrap_or_default(), e.seat.clone().unwrap_or_default());
                }
            }
            if !d.data.results.is_empty() {
                println!();
                println!("RESULTS");
                for r in &d.data.results {
                    println!("  {:<10}  {:<8}  {:<28}  {}", r.date, r.code, truncate(&r.title, 28), r.result);
                }
            }
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Notices => {
            let (d, src) = surface(&cache, "noticeboard", TTL_SLOW, args.refresh, args.offline, || client.noticeboard()).await?;
            if args.json { return print_json(&d.data); }
            for n in &d.data.noticeboard {
                println!("  {} ({})", n.name, n.filename.clone().unwrap_or_default());
            }
            println!("(documents listed only, never auto-downloaded)");
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Cal { date } => {
            let date = date.clone().unwrap_or_else(|| today.clone());
            let (d, src) = surface(&cache, "calendar", TTL_SLOW, args.refresh, args.offline, || client.calendar(&learner, &date)).await?;
            if args.json { return print_json(&d.data); }
            for e in &d.data.calendar {
                let start = e.start_date.get(0..10).unwrap_or(&e.start_date);
                println!("  {:<10}  {}{}", start, e.description, e.notes.as_ref().map(|n| format!(" ({n})")).unwrap_or_default());
            }
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Clubs => {
            let (d, src) = surface(&cache, "clubs", TTL_SLOW, args.refresh, args.offline, || client.clubs()).await?;
            if args.json { return print_json(&d.data); }
            for c in &d.data.clubs {
                println!("  {}", c.name);
                if let Some(desc) = &c.description {
                    let t = strip_html(desc);
                    if !t.is_empty() { println!("    {}", truncate(&t, 70)); }
                }
            }
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Me => {
            let (d, src) = surface(&cache, "personal", TTL_SLOW, args.refresh, args.offline, || client.personal()).await?;
            if args.json { return print_json(&d.data); }
            if let Some(obj) = d.data.personal.as_object() {
                for (k, v) in obj {
                    let val = match v { serde_json::Value::String(s) => s.clone(), other => other.to_string() };
                    println!("  {:<22} {}", k.replace('_', " "), val);
                }
            }
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Msg => {
            let (d, src) = surface(&cache, "inbox", TTL_POINTS, args.refresh, args.offline, || client.inbox()).await?;
            if args.json { return print_json(&d.data); }
            for m in &d.data.messages {
                let unread = m.read.is_none() || m.read.as_deref() == Some("");
                println!("  {}{:<16}  {:<30}  {}",
                    if unread { "* " } else { "  " },
                    m.date,
                    truncate(&m.subject, 30),
                    m.sender.as_ref().map(|s| s.name.as_str()).unwrap_or(""));
            }
            println!("(* = unread; reading here never marks it read in Edulink)");
            println!(); println!("{}", footnote(&src)); Ok(())
        }
        Cmd::Login { .. } | Cmd::Logout { .. } | Cmd::Accounts | Cmd::Switch { .. } | Cmd::Tui => unreachable!(),
    }
}

pub async fn main() -> Result<()> {
    let args = Args::parse();
    let (config_dir, cache_dir) = dirs()?;

    if let Some(dir) = &args.mock {
        // Mock mode: no network, no session required.
        let transport = MockTransport::new(dir);
        let client = Client::new(transport, "mock.local", "fixture-user");
        let state_dir = std::env::temp_dir().join("edulink-two-mock");
        std::fs::create_dir_all(&state_dir)?;
        match &args.cmd {
            Cmd::Login { username: _, learner_id, .. } => {
                let today = Local::now().format("%Y-%m-%d").to_string();
                let (login, pre) = client
                    .login("fixture-password", 81, learner_id.as_deref().or(Some("424242")), &today)
                    .await?;
                println!(
                    "mock login ok: {} (token established), prefetch {}ms",
                    login.user.as_ref().map(|u| u.display()).unwrap_or_default(),
                    pre.total_ms
                );
                return Ok(());
            }
            Cmd::Logout { .. } => return Ok(()),
            Cmd::Accounts => {
                println!("fixture-user@mock.local (current)");
                return Ok(());
            }
            Cmd::Switch { .. } => return Ok(()),
            _ => {
                let session = Session {
                    host: "mock.local".into(),
                    authtoken: "fixture".into(),
                    username: "fixture-user".into(),
                    user_id: "424242".into(),
                    learner_id: "424242".into(),
                    display_name: "Fixture User".into(),
                    school: "Mock School".into(),
                    school_code: "MOCK 1CK".into(),
                    school_id: 81,
                    saved_at: Utc::now(),
                };
                let _ = &state_dir;
                return run_with(&args, client, &session, &cache_dir).await;
            }
        }
    }

    match &args.cmd {
        Cmd::Login { username, code, host, establishment, learner_id } => {
            // Resolve the school exactly like the stock app: code/postcode
            // -> host + establishment id. Explicit --host/--establishment
            // skips the lookup.
            let (host, school_id) = if let Some(code) = code {
                let info = crate::client::resolve_school(code).await?;
                println!("{} (id {})", info.host, info.school_id);
                (info.host, info.school_id)
            } else {
                let h = host.clone().unwrap_or_else(|| DEFAULT_HOST.into());
                let id = establishment.context("need --code (school ID or postcode) or --host + --establishment")?;
                (h, id)
            };
            let transport = HttpTransport::new(&host)?;
            if args.poor {
                transport.set_degraded(true);
            }
            let client = Client::new(transport, host.clone(), username.clone());
            let password = rpassword::prompt_password("Password: ")?;
            let today = Local::now().format("%Y-%m-%d").to_string();
            let t = Instant::now();
            let (login, pre) = client
                .login(&password, school_id, learner_id.as_deref(), &today)
                .await
                .context("login failed")?;
            let school = login
                .establishment
                .as_ref()
                .and_then(|e| e.name.clone())
                .unwrap_or_default();
            let user = login.user.clone().context("no user identity")?;
            let session = Session {
                host: host.clone(),
                authtoken: login.authtoken.clone(),
                username: username.clone(),
                user_id: user.id.to_string(),
                learner_id: learner_id
                    .clone()
                    .unwrap_or_else(|| user.id.to_string()),
                display_name: user.display(),
                school: school.clone(),
                school_code: code.clone().unwrap_or_default(),
                school_id,
                saved_at: Utc::now(),
            };
            let mut store = AccountStore::load(&config_dir)?;
            store.upsert(session.clone());
            store.save(&config_dir)?;
            let cache = Cache::new(&cache_dir)?.for_user(username)?;
            let mut ok = 0u8;
            if let Ok(d) = pre.homework.result { cache.write("homework", &d).await?; ok += 1; }
            if let Ok(d) = pre.timetable.result { cache.write("timetable", &d).await?; ok += 1; }
            if let Ok(d) = pre.achievement.result { cache.write("achievement", &d).await?; ok += 1; }
            if let Ok(d) = pre.behaviour.result { cache.write("behaviour", &d).await?; ok += 1; }
            println!(
                "Logged in as {} @ {} ({}ms). Pre-cached {}/4 surfaces - everything opens instantly now.",
                session.display_name,
                if school.is_empty() { host.as_str() } else { school.as_str() },
                t.elapsed().as_millis(),
                ok
            );
            Ok(())
        }
        Cmd::Logout { username } => {
            let mut store = AccountStore::load(&config_dir)?;
            let target = username
                .clone()
                .or_else(|| store.current().map(|s| s.username.clone()));
            match target {
                Some(t) if store.remove(&t) => {
                    store.save(&config_dir)?;
                    println!("removed account {t} (cache kept)");
                }
                Some(t) => println!("no saved account '{t}'"),
                None => println!("no accounts saved"),
            }
            Ok(())
        }
        Cmd::Accounts => {
            let store = AccountStore::load(&config_dir)?;
            if store.accounts.is_empty() {
                println!("no accounts saved - run `edulink login <username>`");
            }
            for a in &store.accounts {
                let mark = if Some(a.key()) == store.current { " (current)" } else { "" };
                println!("{} - {} @ {}{}", a.key(), a.display_name, a.school, mark);
            }
            Ok(())
        }
        Cmd::Switch { username } => {
            let mut store = AccountStore::load(&config_dir)?;
            store.set_current(username)?;
            store.save(&config_dir)?;
            let cur = store.current().unwrap();
            println!("switched to {} - {} @ {}", cur.key(), cur.display_name, cur.school);
            Ok(())
        }
        _ => {
            let store = AccountStore::load(&config_dir)?;
            let session = store
                .current()
                .ok_or_else(|| anyhow!("not logged in - run `edulink login <username>`"))?
                .clone();
            let transport = HttpTransport::new(&session.host)?;
            transport.set_token(&session.authtoken);
            if args.poor {
                transport.set_degraded(true);
            }
            let client = Client::new(transport, session.host.clone(), session.username.clone());
            run_with(&args, client, &session, &cache_dir).await
        }
    }
}
