mod error;
mod m3u;
mod hls;
mod player;
mod config;
mod app;
mod ui;
mod event;
mod hls_stream;
mod ts_demux;

use std::io;
use crossterm::{
    event::{DisableMouseCapture, EnableMouseCapture},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{
    backend::CrosstermBackend,
    Terminal,
};
use app::App;
use error::Result;
use m3u::parse_m3u;

fn main() -> Result<()> {
    // Disable logging by default to prevent TUI corruption.
    // Set RUST_LOG env var if you need logs.
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("off"))
        .init();

    let m3u_content = std::fs::read_to_string("cnr.m3u")
        .unwrap_or_else(|_| include_str!("../cnr.m3u").to_string());
    let stations = parse_m3u(&m3u_content)?;

    enable_raw_mode()?;
    let mut stdout = io::stdout();
    execute!(stdout, EnterAlternateScreen, EnableMouseCapture)?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let mut app = App::new(stations)?;

    // Auto-play default station (音乐之声) on startup
    if let Some(station) = app.selected_station() {
        let url = station.url.clone();
        match app.hls_streamer.play_station(&url, &mut app.player) {
            Ok(()) => {
                app.playback_state = app::PlaybackState::Playing;
            }
            Err(err) => {
                app.error_message = Some(err.to_string());
            }
        }
    }

    loop {
        terminal.draw(|f| ui::draw(f, &app))?;

        event::handle_events(&mut app)?;

        if app.should_quit {
            break;
        }
    }

    disable_raw_mode()?;
    execute!(
        terminal.backend_mut(),
        LeaveAlternateScreen,
        DisableMouseCapture
    )?;
    terminal.show_cursor()?;

    Ok(())
}