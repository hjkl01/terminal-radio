use crate::app::{App, PlaybackState};
use ratatui::{
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Gauge, List, ListItem, ListState, Paragraph},
    Frame,
};

const KEY_STYLE: Style = Style::new().fg(Color::Cyan).add_modifier(Modifier::BOLD);
const MUTED_STYLE: Style = Style::new().fg(Color::DarkGray);

pub fn draw(f: &mut Frame, app: &App) {
    draw_main(f, f.area(), app);
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
    let items: Vec<ListItem> = app
        .stations
        .iter()
        .enumerate()
        .map(|(i, station)| {
            let prefix = if i == app.selected_index {
                "▸ "
            } else {
                "  "
            };
            let star = if app.is_favorite(&station.name) {
                " ★"
            } else {
                ""
            };
            let style = if i == app.selected_index {
                Style::default()
                    .fg(Color::Yellow)
                    .add_modifier(Modifier::BOLD)
            } else {
                Style::default()
            };
            ListItem::new(Line::from(vec![Span::styled(
                format!("{}{}{}", prefix, station.name, star),
                style,
            )]))
        })
        .collect();

    let list = List::new(items).block(
        Block::default()
            .title("电台列表")
            .title_style(Style::default().fg(Color::Green))
            .borders(Borders::ALL)
            .border_style(Style::default().fg(Color::DarkGray)),
    );

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
            Constraint::Length(5),
            Constraint::Min(0),
        ])
        .split(area);

    let station_name = app
        .selected_station()
        .map(|s| s.name.as_str())
        .unwrap_or("未选择");

    let status = match app.playback_state {
        PlaybackState::Stopped => "⏹ 停止",
        PlaybackState::Playing => "▶ 播放中",
        PlaybackState::Paused => "⏸ 暂停",
    };

    let now_playing = Paragraph::new(station_name)
        .style(Style::default().add_modifier(Modifier::BOLD))
        .block(
            Block::default()
                .title("现在播放")
                .title_style(Style::default().fg(Color::Magenta))
                .borders(Borders::ALL)
                .border_style(Style::default().fg(Color::DarkGray)),
        );
    f.render_widget(now_playing, chunks[0]);

    let status_widget = Paragraph::new(status)
        .style(Style::default().fg(status_color(&app.playback_state)))
        .block(
            Block::default()
                .title("状态")
                .title_style(Style::default().fg(Color::Blue))
                .borders(Borders::ALL)
                .border_style(Style::default().fg(Color::DarkGray)),
        );
    f.render_widget(status_widget, chunks[1]);

    let volume = app.volume_percent();
    let volume_color = if volume >= 66 {
        Color::Green
    } else if volume >= 33 {
        Color::Yellow
    } else {
        Color::Red
    };
    let volume_gauge = Gauge::default()
        .block(
            Block::default()
                .title("音量")
                .title_style(Style::default().fg(Color::Green))
                .borders(Borders::ALL)
                .border_style(Style::default().fg(Color::DarkGray)),
        )
        .gauge_style(Style::default().fg(volume_color))
        .ratio(volume as f64 / 100.0)
        .label(format!("{}%", volume));
    f.render_widget(volume_gauge, chunks[2]);

    let shortcuts = Paragraph::new(shortcut_lines()).block(
        Block::default()
            .title("快捷键")
            .title_style(Style::default().fg(Color::Cyan))
            .borders(Borders::ALL)
            .border_style(Style::default().fg(Color::DarkGray)),
    );
    f.render_widget(shortcuts, chunks[3]);

    if let Some(err) = &app.error_message {
        let error_msg = Paragraph::new(err.as_str()).style(Style::default().fg(Color::Red));
        f.render_widget(error_msg, chunks[4]);
    }
}

fn status_color(state: &PlaybackState) -> Color {
    match state {
        PlaybackState::Stopped => Color::Red,
        PlaybackState::Playing => Color::Green,
        PlaybackState::Paused => Color::Yellow,
    }
}

fn shortcut_lines() -> Vec<Line<'static>> {
    let row1: Vec<(&'static str, &'static str)> =
        vec![("↑↓", "切换"), ("Enter", "播放"), ("Space", "暂停")];
    let row2: Vec<(&'static str, &'static str)> =
        vec![("+/-", "音量"), ("f", "收藏"), ("q", "退出")];

    vec![Line::from(hint_spans(&row1)), Line::from(hint_spans(&row2))]
}

fn hint_spans(hints: &[(&'static str, &'static str)]) -> Vec<Span<'static>> {
    let mut spans = Vec::new();
    for (i, (key, desc)) in hints.iter().enumerate() {
        spans.push(Span::styled(*key, KEY_STYLE));
        spans.push(Span::styled(format!(" {}", desc), MUTED_STYLE));
        if i < hints.len() - 1 {
            spans.push(Span::styled(" │ ", Style::default().fg(Color::Gray)));
        }
    }
    spans
}
