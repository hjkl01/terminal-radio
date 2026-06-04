use std::collections::HashSet;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;
use reqwest::blocking::Client;
use crate::error::{AppError, Result};
use crate::hls::fetch_hls_playlist;
use crate::player::AudioPlayer;

pub struct HlsStreamer {
    client: Client,
    stop_flag: Arc<AtomicBool>,
    worker: Option<thread::JoinHandle<()>>,
}

impl HlsStreamer {
    pub fn new() -> Self {
        Self {
            client: Client::builder()
                .user_agent("Mozilla/5.0")
                .build()
                .unwrap_or_else(|_| Client::new()),
            stop_flag: Arc::new(AtomicBool::new(false)),
            worker: None,
        }
    }

    pub fn play_station(&mut self, url: &str, player: &mut AudioPlayer) -> Result<()> {
        self.stop();
        self.stop_flag.store(false, Ordering::Relaxed);

        let playlist = fetch_hls_playlist(&self.client, url)?;

        if playlist.segments.is_empty() {
            return Err(AppError::Hls("No segments available".to_string()));
        }

        let first_segment_url = playlist.segments[0].url.clone();
        let audio_data = self.download_segment(&first_segment_url)?;

        player.play(audio_data)?;

        let client = self.client.clone();
        let playlist_url = url.to_string();
        let stop_flag = self.stop_flag.clone();
        let player_buffer = player.shared_buffer();

        self.worker = Some(thread::spawn(move || {
            let mut seen_segments: HashSet<String> = HashSet::new();
            seen_segments.insert(first_segment_url);

            while !stop_flag.load(Ordering::Relaxed) {
                let playlist = match fetch_hls_playlist(&client, &playlist_url) {
                    Ok(p) => p,
                    Err(_) => {
                        thread::sleep(Duration::from_millis(500));
                        continue;
                    }
                };

                for seg in playlist.segments {
                    if stop_flag.load(Ordering::Relaxed) {
                        break;
                    }

                    if seen_segments.contains(&seg.url) {
                        continue;
                    }

                    match download_segment_with_client(&client, &seg.url) {
                        Ok(bytes) => {
                            if let Ok(samples) = AudioPlayer::decode_audio_bytes(&bytes) {
                                if let Ok(mut buf) = player_buffer.lock() {
                                    buf.extend(samples);
                                }
                            }
                            seen_segments.insert(seg.url);
                        }
                        Err(_) => {}
                    }
                }

                let sleep_ms = playlist
                    .target_duration
                    .map(|s| (s * 500.0) as u64)
                    .unwrap_or(1000)
                    .clamp(300, 3000);
                thread::sleep(Duration::from_millis(sleep_ms));
            }

        }));

        Ok(())
    }

    pub fn stop(&mut self) {
        self.stop_flag.store(true, Ordering::Relaxed);
        if let Some(handle) = self.worker.take() {
            let _ = handle.join();
        }
    }

    fn download_segment(&self, url: &str) -> Result<Vec<u8>> {
        let response = self.client.get(url)
            .send()
            .map_err(|e| AppError::Hls(format!("Failed to download segment: {}", e)))?;

        if !response.status().is_success() {
            return Err(AppError::Hls(format!(
                "HTTP {} when downloading segment",
                response.status()
            )));
        }

        let bytes = response.bytes()
            .map_err(|e| AppError::Hls(format!("Failed to read segment: {}", e)))?;

        if bytes.is_empty() {
            return Err(AppError::Hls("Downloaded segment is empty".to_string()));
        }

        Ok(bytes.to_vec())
    }
}

impl Drop for HlsStreamer {
    fn drop(&mut self) {
        self.stop();
    }
}

fn download_segment_with_client(client: &Client, url: &str) -> Result<Vec<u8>> {
    let response = client
        .get(url)
        .send()
        .map_err(|e| AppError::Hls(format!("Failed to download segment: {}", e)))?;

    if !response.status().is_success() {
        return Err(AppError::Hls(format!(
            "HTTP {} when downloading segment",
            response.status()
        )));
    }

    let bytes = response
        .bytes()
        .map_err(|e| AppError::Hls(format!("Failed to read segment: {}", e)))?;

    if bytes.is_empty() {
        return Err(AppError::Hls("Downloaded segment is empty".to_string()));
    }

    Ok(bytes.to_vec())
}
