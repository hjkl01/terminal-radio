use crate::config::Config;
use crate::hls_stream::HlsStreamer;
use crate::m3u::Station;
use crate::player::AudioPlayer;
use crate::error::Result;

const VOLUME_STEP: f32 = 0.05;

#[derive(PartialEq)]
pub enum PlaybackState {
    Stopped,
    Playing,
    Paused,
}

pub struct App {
    pub stations: Vec<Station>,
    pub selected_index: usize,
    pub playback_state: PlaybackState,
    pub config: Config,
    pub player: AudioPlayer,
    pub hls_streamer: HlsStreamer,
    pub should_quit: bool,
    pub error_message: Option<String>,
}

impl App {
    pub fn new(stations: Vec<Station>) -> Result<Self> {
        let config = Config::load()?;
        let player = AudioPlayer::new();
        player.set_volume(config.volume);
        let hls_streamer = HlsStreamer::new();

        // Default to "音乐之声" if available
        let selected_index = stations
            .iter()
            .position(|s| s.name == "音乐之声")
            .unwrap_or(0);

        Ok(Self {
            stations,
            selected_index,
            playback_state: PlaybackState::Stopped,
            config,
            player,
            hls_streamer,
            should_quit: false,
            error_message: None,
        })
    }

    pub fn selected_station(&self) -> Option<&Station> {
        self.stations.get(self.selected_index)
    }

    pub fn select_next(&mut self) {
        if !self.stations.is_empty() {
            self.selected_index = (self.selected_index + 1) % self.stations.len();
        }
    }

    pub fn select_previous(&mut self) {
        if !self.stations.is_empty() {
            self.selected_index = if self.selected_index == 0 {
                self.stations.len() - 1
            } else {
                self.selected_index - 1
            };
        }
    }

    pub fn toggle_favorite(&mut self) {
        if let Some(station) = self.selected_station() {
            let name = station.name.clone();
            self.config.toggle_favorite(&name);
            let _ = self.config.save();
        }
    }

    pub fn is_favorite(&self, station_name: &str) -> bool {
        self.config.is_favorite(station_name)
    }

    pub fn volume_up(&mut self) {
        let volume = (self.player.get_volume() + VOLUME_STEP).min(1.0);
        self.player.set_volume(volume);
        self.config.volume = volume;
        let _ = self.config.save();
    }

    pub fn volume_down(&mut self) {
        let volume = (self.player.get_volume() - VOLUME_STEP).max(0.0);
        self.player.set_volume(volume);
        self.config.volume = volume;
        let _ = self.config.save();
    }

    pub fn volume_percent(&self) -> u8 {
        (self.player.get_volume() * 100.0) as u8
    }

    pub fn quit(&mut self) {
        self.should_quit = true;
    }
}
