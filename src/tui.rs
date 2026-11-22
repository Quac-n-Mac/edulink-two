//! TUI: instant render from cache, background parallel refresh, no
//! spinners. Tab switches are immediate (no animation) - the "60%
//! faster" spec is met by never animating at all.

use crate::cache::{Cache, Cached};
use crate::client::Client;
use crate::models::*;
use crate::transport::Transport;
use anyhow::Result;
use chrono::Local;
use crossterm::{
    event::{self, Event, KeyCode, KeyEventKind},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{
    backend::CrosstermBackend,
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    widgets::{Block, Borders, List, ListItem, Paragraph, Tabs},
    Terminal,
};
use std::io::stdout;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc;

struct State {
    tab: usize,
    homework: Option<Cached<HomeworkData>>,
    timetable: Option<Cached<TimetableData>>,
    achievement: Option<Cached<AchievementData>>,
    behaviour: Option<Cached<BehaviourData>>,
    refreshing: bool,
    offline: bool,
    last_error: Option<String>,
}

enum Update {
    Homework(Cached<HomeworkData>),
    Timetable(Cached<TimetableData>),
    Achievement(Cached<AchievementData>),
    Behaviour(Cached<BehaviourData>),
    Error(String),
    Done,
}

fn age_short(c: &chrono::DateTime<chrono::Utc>) -> String {
    let s = (chrono::Utc::now() - *c).num_seconds().max(0);
    if s < 90 {
        format!("{s}s")
    } else if s < 5400 {
        format!("{}m", s / 60)
    } else {
        format!("{}h", s / 3600)
    }
}

pub async fn run<T: Transport + 'static>(
    client: Client<T>,
    cache: Cache,
    learner_id: String,
    offline: bool,
) -> Result<()> {
    let mut state = State {
        tab: 0,
        homework: cache.read_sync("homework").ok(),
        timetable: cache.read_sync("timetable").ok(),
        achievement: cache.read_sync("achievement").ok(),
        behaviour: cache.read_sync("behaviour").ok(),
        refreshing: false,
        offline,
        last_error: None,
    };

    let client = Arc::new(client);
    let (tx, mut rx) = mpsc::unbounded_channel::<Update>();

    let spawn_refresh = |client: Arc<Client<T>>, cache: Cache, learner: String, tx: mpsc::UnboundedSender<Update>| {
        tokio::spawn(async move {
            let date = Local::now().format("%Y-%m-%d").to_string();
            let pre = client.prefetch_all(&learner, &date).await;
            if let Ok(d) = pre.homework.result {
                let _ = cache.write("homework", &d).await;
                let _ = tx.send(Update::Homework(Cached { fetched_at: chrono::Utc::now(), data: d }));
            } else if let Err(e) = pre.homework.result {
                let _ = tx.send(Update::Error(format!("homework: {e:#}")));
            }
            if let Ok(d) = pre.timetable.result {
                let _ = cache.write("timetable", &d).await;
                let _ = tx.send(Update::Timetable(Cached { fetched_at: chrono::Utc::now(), data: d }));
            } else if let Err(e) = pre.timetable.result {
                let _ = tx.send(Update::Error(format!("timetable: {e:#}")));
            }
            if let Ok(d) = pre.achievement.result {
                let _ = cache.write("achievement", &d).await;
                let _ = tx.send(Update::Achievement(Cached { fetched_at: chrono::Utc::now(), data: d }));
            } else if let Err(e) = pre.achievement.result {
                let _ = tx.send(Update::Error(format!("achievement: {e:#}")));
            }
            if let Ok(d) = pre.behaviour.result {
                let _ = cache.write("behaviour", &d).await;
                let _ = tx.send(Update::Behaviour(Cached { fetched_at: chrono::Utc::now(), data: d }));
            } else if let Err(e) = pre.behaviour.result {
                let _ = tx.send(Update::Error(format!("behaviour: {e:#}")));
            }
            let _ = tx.send(Update::Done);
        });
    };

    enable_raw_mode()?;
    let mut out = stdout();
    execute!(out, EnterAlternateScreen)?;
    let backend = CrosstermBackend::new(out);
    let mut terminal = Terminal::new(backend)?;

    // Background refresh fires immediately (unless offline).
    if !offline {
        state.refreshing = true;
        spawn_refresh(client.clone(), cache.clone(), learner_id.clone(), tx.clone());
    }

    let result = loop {
        terminal.draw(|f| draw(f, &state)).ok();

        // Drain updates.
        while let Ok(u) = rx.try_recv() {
            match u {
                Update::Homework(c) => state.homework = Some(c),
                Update::Timetable(c) => state.timetable = Some(c),
                Update::Achievement(c) => state.achievement = Some(c),
                Update::Behaviour(c) => state.behaviour = Some(c),
                Update::Error(e) => state.last_error = Some(e),
                Update::Done => state.refreshing = false,
            }
        }

        if event::poll(Duration::from_millis(60))? {
            if let Event::Key(k) = event::read()? {
                if k.kind == KeyEventKind::Press {
                    match k.code {
                        KeyCode::Char('q') | KeyCode::Esc => break Ok(()),
                        KeyCode::Right | KeyCode::Tab | KeyCode::Char('l') => {
                            state.tab = (state.tab + 1) % 3;
                        }
                        KeyCode::Left | KeyCode::BackTab | KeyCode::Char('h') => {
                            state.tab = (state.tab + 2) % 3;
                        }
                        KeyCode::Char('r') => {
                            if !state.refreshing && !offline {
                                state.refreshing = true;
                                state.last_error = None;
                                spawn_refresh(client.clone(), cache.clone(), learner_id.clone(), tx.clone());
                            }
                        }
                        _ => {}
                    }
                }
            }
        }
    };

    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)?;
    result
}

fn draw(f: &mut ratatui::Frame, state: &State) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Min(1),
            Constraint::Length(1),
        ])
        .split(f.area());

    let titles = ["Homework", "Timetable", "Points"];
    let tabs = Tabs::new(titles.iter().map(|t| (*t).to_string()).collect::<Vec<_>>())
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title(" Edulink Two "),
        )
        .select(state.tab)
        .style(Style::default().fg(Color::Gray))
        .highlight_style(
            Style::default()
                .fg(Color::Cyan)
                .add_modifier(Modifier::BOLD),
        );
    f.render_widget(tabs, chunks[0]);

    let body: Vec<ListItem> = match state.tab {
        0 => homework_items(state),
        1 => timetable_items(state),
        _ => points_items(state),
    };
    let status = if state.refreshing {
        "refreshing...".to_string()
    } else if state.offline {
        "offline - last-known data".to_string()
    } else if let Some(e) = &state.last_error {
        format!("last refresh error: {e}")
    } else {
        let age = state
            .homework
            .as_ref()
            .map(|c| age_short(&c.fetched_at))
            .unwrap_or_else(|| "n/a".into());
        format!("updated {age} ago")
    };
    let list = List::new(body).block(
        Block::default()
            .borders(Borders::ALL)
            .title(format!(" {status} ")),
    );
    f.render_widget(list, chunks[1]);

    let footer = Paragraph::new("left/right: tabs  r: refresh  q: quit")
        .style(Style::default().fg(Color::DarkGray));
    f.render_widget(footer, chunks[2]);
}

fn homework_items(state: &State) -> Vec<ListItem<'static>> {
    match &state.homework {
        None => vec![ListItem::new("no homework cached yet")],
        Some(c) => {
            let mut v: Vec<ListItem<'static>> = Vec::new();
            if c.data.homework.current.is_empty() {
                v.push(ListItem::new("no current homework".to_string()));
            }
            for h in &c.data.homework.current {
                let line = format!(
                    "{:<10}  {:<12}  {}  {}",
                    h.due_date,
                    h.subject,
                    h.activity,
                    h.status.clone().unwrap_or_default()
                );
                v.push(ListItem::new(line));
            }
            if !c.data.homework.past.is_empty() {
                v.push(ListItem::new(""));
                v.push(ListItem::new(format!("--- past ({}) ---", c.data.homework.past.len())));
                for h in c.data.homework.past.iter().take(10) {
                    v.push(ListItem::new(format!(
                        "{:<10}  {:<12}  {}",
                        h.due_date, h.subject, h.activity
                    )));
                }
            }
            v
        }
    }
}

fn timetable_items(state: &State) -> Vec<ListItem<'static>> {
    match &state.timetable {
        None => vec![ListItem::new("no timetable cached yet")],
        Some(c) => {
            let today = Local::now().format("%Y-%m-%d").to_string();
            let mut v: Vec<ListItem<'static>> = Vec::new();
            for week in &c.data.weeks {
                for day in &week.days {
                    let marker = if day.date == today { " <-- today" } else { "" };
                    v.push(ListItem::new(format!(
                        "{} ({}){}",
                        day.name.clone().unwrap_or_default(),
                        day.date,
                        marker
                    )));
                    for l in &day.lessons {
                        v.push(ListItem::new(format!(
                            "   {:<5}-{:<5}  {:<16}  {:<8}  {}",
                            l.start_time.clone().unwrap_or_default(),
                            l.end_time.clone().unwrap_or_default(),
                            l.subject_name(),
                            l.room_name(),
                            l.teacher_name()
                        )));
                    }
                }
            }
            if v.is_empty() {
                v.push(ListItem::new("no lessons in cache".to_string()));
            }
            v
        }
    }
}

fn points_items(state: &State) -> Vec<ListItem<'static>> {
    let (a, b) = match (&state.achievement, &state.behaviour) {
        (Some(a), Some(b)) => (a, b),
        _ => return vec![ListItem::new("no points cached yet")],
    };
    let p = PointsSummary::build(&a.data, &b.data);
    let mut v: Vec<ListItem<'static>> = Vec::new();
    v.push(ListItem::new(format!(
        "achievement +{}   behaviour -{}   net {}",
        p.achievement_points,
        p.behaviour_points,
        p.achievement_points - p.behaviour_points
    )));
    v.push(ListItem::new(""));
    v.push(ListItem::new("ACHIEVEMENTS"));
    for e in p.achievement_events.iter().take(10) {
        v.push(ListItem::new(format!(
            "  {}  +{:<3}  {}",
            e.date,
            e.points,
            e.comments.clone().unwrap_or_default()
        )));
    }
    v.push(ListItem::new(""));
    v.push(ListItem::new("BEHAVIOUR"));
    for e in p.behaviour_events.iter().take(10) {
        v.push(ListItem::new(format!(
            "  {}  -{:<3}  {}",
            e.date,
            e.points,
            e.comments.clone().unwrap_or_default()
        )));
    }
    if !p.detentions.is_empty() {
        v.push(ListItem::new(""));
        v.push(ListItem::new("DETENTIONS"));
        for d in &p.detentions {
            v.push(ListItem::new(format!(
                "  {}  {}  {}  {}",
                d.date.clone().unwrap_or_default(),
                d.description.clone().unwrap_or_default(),
                d.location.clone().unwrap_or_default(),
                d.attended.clone().unwrap_or_default()
            )));
        }
    }
    v
}
