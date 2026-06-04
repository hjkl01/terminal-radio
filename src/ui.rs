use ratatui::{
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Gauge, List, ListItem, ListState, Paragraph},
    Frame,
};
use crate::app::{App, PlaybackState};

pub fn draw(f: &mut Frame, app: &App) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Min(10),
            Constraint::Length(3),
        ])
        .split(f.area());

    draw_header(f, chunks[0]);
    draw_main(f, chunks[1], app);
    draw_footer(f, chunks[2]);
}

fn draw_header(f: &mut Frame, area: Rect) {
    let header = Paragraph::new("Terminal Radio v0.1.0")
        .style(Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD))
        .block(Block::default().borders(Borders::ALL));
    f.render_widget(header, area);
}

fn draw_main(f: &mut Frame, area: Rect, app: &App) {
    let chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(40), Constraint::Percentage(60)])
        .split(area);

    draw_station_list(f, chunks[0], app);
    draw_playback_info(f, chunks[1], app);
}

fn draw_station_list(f: &mut Frame, area: Rect, app: &App) {
    let items: Vec<ListItem> = app.stations
        .iter()
        .enumerate()
        .map(|(i, station)| {
            let prefix = if i == app.selected_index { "> " } else { "  " };
            let star = if app.is_favorite(&station.name) { " ★" } else { "" };
            let style = if i == app.selected_index {
                Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD)
            } else {
                Style::default()
            };
            ListItem::new(Line::from(vec![
                Span::styled(format!("{}{}{}", prefix, station.name, star), style),
            ]))
        })
        .collect();

    let list = List::new(items)
        .block(Block::default().title("电台列表").borders(Borders::ALL));

    let mut state = ListState::default();
    state.select(Some(app.selected_index));

    f.render_stateful_widget(list, area, &mut state);
}

fn draw_playback_info(f: &mut Frame, area: Rect, app: &App) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Length(3),
            Constraint::Length(3),
            Constraint::Length(1),
        ])
        .split(area);

    let station_name = app.selected_station()
        .map(|s| s.name.as_str())
        .unwrap_or("未选择");

    let status = match app.playback_state {
        PlaybackState::Stopped => "⏹ 停止",
        PlaybackState::Playing => "▶ 播放中",
        PlaybackState::Paused => "⏸ 暂停",
    };

    let now_playing = Paragraph::new(format!("电台: {}", station_name))
        .block(Block::default().title("现在播放").borders(Borders::ALL));
    f.render_widget(now_playing, chunks[0]);

    let status_widget = Paragraph::new(format!("状态: {}", status))
        .block(Block::default().borders(Borders::ALL));
    f.render_widget(status_widget, chunks[1]);

    let volume = app.volume_percent();
    let volume_gauge = Gauge::default()
        .block(Block::default().title("音量").borders(Borders::ALL))
        .gauge_style(Style::default().fg(Color::Green))
        .ratio(volume as f64 / 100.0)
        .label(format!("{}%", volume));
    f.render_widget(volume_gauge, chunks[2]);

    if let Some(err) = &app.error_message {
        let error_msg = Paragraph::new(err.as_str())
            .style(Style::default().fg(Color::Red));
        f.render_widget(error_msg, chunks[3]);
    }
}

fn draw_footer(f: &mut Frame, area: Rect) {
    let footer = Paragraph::new("↑↓ 切换 │ Enter 播放 │ Space 暂停 │ +/- 音量 │ f 收藏 │ q 退出")
        .style(Style::default().fg(Color::DarkGray))
        .block(Block::default().borders(Borders::ALL));
    f.render_widget(footer, area);
}