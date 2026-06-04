use crossterm::event::{self, Event, KeyCode, KeyEvent, KeyModifiers};
use std::time::Duration;
use crate::app::App;
use crate::error::Result;

pub fn handle_events(app: &mut App) -> Result<()> {
    // Keep stream alive when audio device changes (e.g. Bluetooth headphones disconnect)
    if app.playback_state == crate::app::PlaybackState::Playing {
        let _ = app.player.ensure_stream_active();
    }

    if event::poll(Duration::from_millis(100))? {
        if let Event::Key(key) = event::read()? {
            handle_key_event(app, key);
        }
    }
    Ok(())
}

fn handle_key_event(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Char('q') | KeyCode::Esc => {
            app.quit();
        }
        KeyCode::Char('c') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.quit();
        }
        KeyCode::Up | KeyCode::Char('k') => {
            app.select_previous();
        }
        KeyCode::Down | KeyCode::Char('j') => {
            app.select_next();
        }
        KeyCode::Enter => {
            if let Some(station) = app.selected_station() {
                let url = station.url.clone();
                match app.hls_streamer.play_station(&url, &mut app.player) {
                    Ok(()) => {
                        app.playback_state = crate::app::PlaybackState::Playing;
                        app.error_message = None;
                    }
                    Err(err) => {
                        app.playback_state = crate::app::PlaybackState::Stopped;
                        app.error_message = Some(err.to_string());
                    }
                }
            }
        }
        KeyCode::Char(' ') => {
            match app.playback_state {
                crate::app::PlaybackState::Playing => {
                    app.player.pause();
                    app.playback_state = crate::app::PlaybackState::Paused;
                }
                crate::app::PlaybackState::Paused => {
                    app.player.resume();
                    app.playback_state = crate::app::PlaybackState::Playing;
                }
                _ => {}
            }
        }
        KeyCode::Char('+') | KeyCode::Char('=') => {
            app.volume_up();
        }
        KeyCode::Char('-') => {
            app.volume_down();
        }
        KeyCode::Char('f') => {
            app.toggle_favorite();
        }
        _ => {}
    }
}
