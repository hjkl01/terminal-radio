use std::collections::VecDeque;
use std::io::Cursor;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use symphonia::default::{get_codecs, get_probe};
use crate::error::{AppError, Result};

pub struct AudioPlayer {
    volume: Arc<Mutex<f32>>,
    playing: Arc<Mutex<bool>>,
    paused: Arc<Mutex<bool>>,
    pcm_buffer: Arc<Mutex<VecDeque<f32>>>,
    stream: Option<cpal::Stream>,
    stream_broken: Arc<AtomicBool>,
    last_rebuild: Option<Instant>,
}

impl AudioPlayer {
    pub fn new() -> Self {
        Self {
            volume: Arc::new(Mutex::new(0.66)),
            playing: Arc::new(Mutex::new(false)),
            paused: Arc::new(Mutex::new(false)),
            pcm_buffer: Arc::new(Mutex::new(VecDeque::new())),
            stream: None,
            stream_broken: Arc::new(AtomicBool::new(false)),
            last_rebuild: None,
        }
    }

    pub fn set_volume(&self, volume: f32) {
        if let Ok(mut v) = self.volume.lock() {
            *v = volume.clamp(0.0, 1.0);
        }
    }

    pub fn get_volume(&self) -> f32 {
        self.volume.lock().map(|v| *v).unwrap_or(0.8)
    }

    #[allow(dead_code)]
    pub fn is_playing(&self) -> bool {
        self.playing.lock().map(|p| *p).unwrap_or(false)
    }

    #[allow(dead_code)]
    pub fn is_paused(&self) -> bool {
        self.paused.lock().map(|p| *p).unwrap_or(false)
    }

    pub fn pause(&self) {
        if let Ok(mut paused) = self.paused.lock() {
            *paused = true;
        }
    }

    pub fn resume(&self) {
        if let Ok(mut paused) = self.paused.lock() {
            *paused = false;
        }
    }

    pub fn shared_buffer(&self) -> Arc<Mutex<VecDeque<f32>>> {
        self.pcm_buffer.clone()
    }

    #[allow(dead_code)]
    pub fn shared_pause_state(&self) -> Arc<Mutex<bool>> {
        self.paused.clone()
    }

    #[allow(dead_code)]
    pub fn shared_volume(&self) -> Arc<Mutex<f32>> {
        self.volume.clone()
    }

    pub fn play(&mut self, data: Vec<u8>) -> Result<()> {
        let decoded_samples = Self::decode_audio_to_pcm(&data)?;
        {
            let mut buffer = self
                .pcm_buffer
                .lock()
                .map_err(|_| AppError::Audio("Failed to lock PCM buffer".to_string()))?;
            buffer.clear();
            buffer.extend(decoded_samples);
        }

        self.resume();
        self.ensure_stream_started()?;

        Ok(())
    }

    #[allow(dead_code)]
    pub fn enqueue_segment(&self, data: Vec<u8>) -> Result<()> {
        let decoded_samples = Self::decode_audio_to_pcm(&data)?;
        let mut buffer = self
            .pcm_buffer
            .lock()
            .map_err(|_| AppError::Audio("Failed to lock PCM buffer".to_string()))?;
        buffer.extend(decoded_samples);
        Ok(())
    }

    pub fn decode_audio_bytes(data: &[u8]) -> Result<Vec<f32>> {
        Self::decode_audio_to_pcm(data)
    }

    fn ensure_stream_started(&mut self) -> Result<()> {
        if self.stream_broken.load(Ordering::Relaxed) {
            self.stream = None;
            self.stream_broken.store(false, Ordering::Relaxed);
        }

        if self.stream.is_some() {
            return Ok(());
        }

        let host = cpal::default_host();
        let device = host.default_output_device()
            .ok_or_else(|| AppError::Audio("No output device found".to_string()))?;

        let supported_config = device.default_output_config()
            .map_err(|e| AppError::Audio(format!("No supported config: {}", e)))?;

        let config = supported_config.config();

        let volume = self.volume.clone();
        let playing = self.playing.clone();
        let paused = self.paused.clone();
        let pcm_buffer = self.pcm_buffer.clone();
        let prebuffer_samples = (config.sample_rate.0 as usize / 2).max(2048);
        let stream_broken = self.stream_broken.clone();

        let stream = device.build_output_stream(
            &config,
            move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                let vol = volume.lock().map(|v| *v).unwrap_or(0.8);
                let is_paused = paused.lock().map(|p| *p).unwrap_or(false);

                if is_paused {
                    for sample in data.iter_mut() {
                        *sample = 0.0;
                    }
                    return;
                }

                if let Ok(mut buffer) = pcm_buffer.lock() {
                    AudioPlayer::mix_from_buffer(&mut buffer, data, vol, prebuffer_samples);
                } else {
                    for sample in data.iter_mut() {
                        *sample = 0.0;
                    }
                }

                if let Ok(mut p) = playing.lock() {
                    *p = true;
                }
            },
            move |_err| {
                stream_broken.store(true, Ordering::Relaxed);
            },
            None,
        ).map_err(|e| AppError::Audio(format!("Failed to build stream: {}", e)))?;

        stream.play().map_err(|e| AppError::Audio(format!("Failed to play: {}", e)))?;
        self.stream = Some(stream);

        Ok(())
    }

    pub fn ensure_stream_active(&mut self) -> Result<()> {
        let is_paused = self.paused.lock().map(|p| *p).unwrap_or(false);
        if is_paused {
            return Ok(());
        }

        if self.stream_broken.load(Ordering::Relaxed) {
            self.stream = None;
            self.stream_broken.store(false, Ordering::Relaxed);
        }

        if self.stream.is_none() {
            let should_attempt = match self.last_rebuild {
                Some(last) => Instant::now().duration_since(last) >= Duration::from_secs(5),
                None => true,
            };
            if should_attempt {
                self.last_rebuild = Some(Instant::now());
                self.ensure_stream_started()?;
            }
        }

        Ok(())
    }

    pub fn stop(&mut self) {
        self.stream = None;
        if let Ok(mut buffer) = self.pcm_buffer.lock() {
            buffer.clear();
        }
        if let Ok(mut p) = self.playing.lock() {
            *p = false;
        }
        if let Ok(mut p) = self.paused.lock() {
            *p = false;
        }
    }

    fn decode_audio_to_pcm(data: &[u8]) -> Result<Vec<f32>> {
        if data.is_empty() {
            return Err(AppError::Audio("Downloaded audio data is empty".to_string()));
        }

        // Detect MPEG-TS format (HLS segments are commonly MPEG-TS)
        if data.len() >= 1 && data[0] == 0x47 {
            return Self::decode_mpeg_ts(data);
        }

        let cursor = Cursor::new(data.to_vec());
        let mss = MediaSourceStream::new(Box::new(cursor), Default::default());

        let mut hint = Hint::new();
        // Try to guess format from magic bytes for common HLS segment types
        if data.len() >= 3 && &data[0..3] == b"ID3" {
            hint.with_extension("mp3");
        } else if data.len() >= 7 && &data[0..7] == b"#EXTM3U" {
            return Err(AppError::Audio("Received HLS playlist instead of audio segment".to_string()));
        }

        let probed = get_probe()
            .format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default())
            .map_err(|e| AppError::Audio(format!("Failed to probe stream format: {e} (data size: {} bytes)", data.len())))?;

        let mut format = probed.format;
        let track = format
            .default_track()
            .ok_or_else(|| AppError::Audio("No default track found in audio stream".to_string()))?;

        let mut decoder = get_codecs()
            .make(&track.codec_params, &DecoderOptions::default())
            .map_err(|e| AppError::Audio(format!("Failed to create decoder: {e}")))?;

        let mut output = Vec::new();

        while let Ok(packet) = format.next_packet() {
            let decoded = decoder
                .decode(&packet)
                .map_err(|e| AppError::Audio(format!("Failed to decode packet: {e}")))?;

            let spec = *decoded.spec();
            let mut sample_buf = SampleBuffer::<f32>::new(decoded.capacity() as u64, spec);
            sample_buf.copy_interleaved_ref(decoded);
            output.extend_from_slice(sample_buf.samples());
        }

        if output.is_empty() {
            return Err(AppError::Audio("Decoded audio is empty".to_string()));
        }

        Ok(output)
    }

    fn decode_mpeg_ts(data: &[u8]) -> Result<Vec<f32>> {
        use symphonia::core::codecs::{CodecParameters, CODEC_TYPE_AAC};
        use symphonia::core::audio::Channels;
        use symphonia::core::formats::Packet;

        let adts_data = crate::ts_demux::extract_adts_from_ts(data)?;

        let adts_info = crate::ts_demux::get_first_adts_info(&adts_data)
            .ok_or_else(|| AppError::Audio("No valid ADTS frames found in MPEG-TS".to_string()))?;

        let sample_rate = crate::ts_demux::sampling_rate_from_index(adts_info.sampling_freq_index);
        let channels_count = crate::ts_demux::channels_count_from_config(adts_info.channel_config);

        // Build AudioSpecificConfig (ASC) for AAC decoder
        let audio_object_type = adts_info.profile + 1;
        let asc = vec![
            ((audio_object_type << 3) | (adts_info.sampling_freq_index >> 1)) as u8,
            (((adts_info.sampling_freq_index & 0x01) << 7) | (adts_info.channel_config << 3)) as u8,
        ];

        let _ = (|| {
            use std::io::Write;
            let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
            writeln!(f, "ADTS profile={}, freq_idx={}, channels={}, aot={}, sr={}",
                adts_info.profile, adts_info.sampling_freq_index, adts_info.channel_config,
                audio_object_type, sample_rate).ok()?;
            writeln!(f, "ASC={:02X} {:02X}", asc[0], asc[1]).ok()?;
            writeln!(f, "ADTS data len={}, first bytes={:02X} {:02X} {:02X} {:02X}",
                adts_data.len(), adts_data[0], adts_data[1], adts_data[2], adts_data[3]).ok()?;
            Some(())
        })();

        let mut codec_params = CodecParameters::new();
        codec_params.for_codec(CODEC_TYPE_AAC);
        codec_params.with_sample_rate(sample_rate);

        let channels = match channels_count {
            1 => Channels::FRONT_CENTRE,
            _ => Channels::FRONT_LEFT | Channels::FRONT_RIGHT,
        };
        codec_params.with_channels(channels);
        codec_params.with_extra_data(asc.into_boxed_slice());

        let mut decoder = get_codecs()
            .make(&codec_params, &DecoderOptions::default())
            .map_err(|e| AppError::Audio(format!("Failed to create AAC decoder: {}", e)))?;

        let mut output = Vec::new();
        let mut offset = 0;
        let mut frame_count = 0;
        let mut error_count = 0;

        while offset + 7 <= adts_data.len() {
            if let Some((frame_length, info)) = crate::ts_demux::parse_adts_header(&adts_data[offset..]) {
                if offset + frame_length > adts_data.len() {
                    break;
                }

                let aac_data = &adts_data[offset + info.header_size..offset + frame_length];
                let packet = Packet::new_from_boxed_slice(
                    0,
                    0,
                    0,
                    aac_data.to_vec().into_boxed_slice(),
                );

                match decoder.decode(&packet) {
                    Ok(decoded) => {
                        let spec = *decoded.spec();
                        let mut sample_buf = SampleBuffer::<f32>::new(decoded.capacity() as u64, spec);
                        sample_buf.copy_interleaved_ref(decoded);
                        output.extend_from_slice(sample_buf.samples());
                        frame_count += 1;
                    }
                    Err(e) => {
                        error_count += 1;
                        if error_count <= 3 {
                            let _ = (|| {
                            use std::io::Write;
                            let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
                            writeln!(f, "Decode error at offset {} (frame_len={}): {}", offset, frame_length, e).ok()?;
                            Some(())
                        })();
                        }
                        // Continue with next frame, don't fail immediately
                    }
                }

                offset += frame_length;
            } else {
                offset += 1;
            }
        }

        let _ = (|| {
            use std::io::Write;
            let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
            writeln!(f, "Decoded {} frames, {} errors, {} samples", frame_count, error_count, output.len()).ok()?;
            Some(())
        })();

        if output.is_empty() {
            return Err(AppError::Audio("Decoded MPEG-TS audio is empty".to_string()));
        }

        Ok(output)
    }

    fn mix_from_buffer(
        buffer: &mut VecDeque<f32>,
        output: &mut [f32],
        volume: f32,
        prebuffer_samples: usize,
    ) {
        if buffer.len() < prebuffer_samples {
            for sample in output.iter_mut() {
                *sample = 0.0;
            }
            return;
        }

        for sample in output.iter_mut() {
            let value = buffer.pop_front().unwrap_or(0.0);
            *sample = value * volume;
        }
    }
}

impl Drop for AudioPlayer {
    fn drop(&mut self) {
        self.stop();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    #[test]
    fn mix_from_buffer_reads_samples_then_pads_silence() {
        let mut queue = VecDeque::from(vec![0.2_f32, -0.2_f32]);
        let mut output = vec![1.0_f32; 4];

        AudioPlayer::mix_from_buffer(&mut queue, &mut output, 1.0, 0);

        assert_eq!(output, vec![0.2, -0.2, 0.0, 0.0]);
        assert!(queue.is_empty());
    }

    #[test]
    fn mix_from_buffer_applies_volume() {
        let mut queue = VecDeque::from(vec![0.5_f32, -0.5_f32]);
        let mut output = vec![0.0_f32; 2];

        AudioPlayer::mix_from_buffer(&mut queue, &mut output, 0.5, 0);

        assert_eq!(output, vec![0.25, -0.25]);
    }

    #[test]
    fn mix_from_buffer_outputs_silence_when_below_prebuffer() {
        let mut queue = VecDeque::from(vec![0.9_f32]);
        let mut output = vec![1.0_f32; 3];

        AudioPlayer::mix_from_buffer(&mut queue, &mut output, 1.0, 2);

        assert_eq!(output, vec![0.0, 0.0, 0.0]);
        assert_eq!(queue.len(), 1);
    }
}
