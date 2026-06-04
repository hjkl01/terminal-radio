use crate::error::{AppError, Result};
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct AdtsInfo {
    pub profile: u8,
    pub sampling_freq_index: u8,
    pub channel_config: u8,
    #[allow(dead_code)]
    pub frame_length: usize,
    pub header_size: usize,
}

#[derive(Debug)]
struct TsPacket {
    pid: u16,
    payload_start: bool,
    #[allow(dead_code)]
    adaptation_field_control: u8,
    #[allow(dead_code)]
    continuity_counter: u8,
    payload: Vec<u8>,
}

fn parse_ts_packet(data: &[u8]) -> Option<TsPacket> {
    if data.len() != 188 || data[0] != 0x47 {
        return None;
    }

    let pid = (((data[1] & 0x1F) as u16) << 8) | (data[2] as u16);
    let payload_start = (data[1] & 0x40) != 0;
    let adaptation_field_control = (data[3] >> 4) & 0x03;
    let continuity_counter = data[3] & 0x0F;

    let mut payload_offset = 4;

    if adaptation_field_control & 0x02 != 0 {
        if data.len() <= 4 {
            return None;
        }
        let adaptation_field_length = data[4] as usize;
        payload_offset += 1 + adaptation_field_length;
    }

    let payload = if adaptation_field_control & 0x01 != 0 && payload_offset < data.len() {
        data[payload_offset..].to_vec()
    } else {
        Vec::new()
    };

    Some(TsPacket {
        pid,
        payload_start,
        adaptation_field_control,
        continuity_counter,
        payload,
    })
}

pub fn parse_adts_header(data: &[u8]) -> Option<(usize, AdtsInfo)> {
    if data.len() < 7 {
        return None;
    }

    let syncword = ((data[0] as u16) << 4) | ((data[1] as u16) >> 4);
    if syncword != 0xFFF {
        return None;
    }

    let _id = (data[1] >> 3) & 0x01;
    let layer = (data[1] >> 1) & 0x03;
    let protection_absent = data[1] & 0x01;
    let profile = (data[2] >> 6) & 0x03;
    let sampling_freq_index = (data[2] >> 2) & 0x0F;
    let channel_config = ((data[2] & 0x01) << 2) | ((data[3] >> 6) & 0x03);
    let frame_length = (((data[3] & 0x03) as usize) << 11)
        | ((data[4] as usize) << 3)
        | ((data[5] as usize) >> 5);

    if layer != 0 || frame_length < 7 || frame_length > 8192 {
        return None;
    }

    let header_size = if protection_absent != 0 { 7 } else { 9 };

    Some((
        frame_length,
        AdtsInfo {
            profile,
            sampling_freq_index,
            channel_config,
            frame_length,
            header_size,
        },
    ))
}

pub fn extract_adts_from_ts(ts_data: &[u8]) -> Result<Vec<u8>> {
    let mut pid_payloads: HashMap<u16, Vec<u8>> = HashMap::new();
    let mut audio_pid: Option<u16> = None;
    let mut transport_errors = 0usize;

    for chunk in ts_data.chunks(188) {
        if chunk.len() != 188 || chunk[0] != 0x47 {
            continue;
        }

        // Check transport error indicator
        if chunk[1] & 0x80 != 0 {
            transport_errors += 1;
            continue;
        }

        let packet = match parse_ts_packet(chunk) {
            Some(p) => p,
            None => continue,
        };

        if packet.payload.is_empty() {
            continue;
        }

        // Detect audio PID by PES start code
        if packet.payload_start && packet.payload.len() >= 4 {
            if packet.payload[0] == 0x00
                && packet.payload[1] == 0x00
                && packet.payload[2] == 0x01
            {
                let stream_id = packet.payload[3];
                // Audio stream ID range: 0xC0 - 0xDF
                if stream_id >= 0xC0 && stream_id <= 0xDF {
                    audio_pid = Some(packet.pid);
                }
            }
        }

        pid_payloads
            .entry(packet.pid)
            .or_insert_with(Vec::new)
            .extend_from_slice(&packet.payload);
    }

    if transport_errors > 0 {
        let _ = (|| {
            use std::io::Write;
            let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
            writeln!(f, "Skipped {} TS packets with transport errors", transport_errors).ok()?;
            Some(())
        })();
    }

    let audio_pid = audio_pid.ok_or_else(|| {
        AppError::Audio("No audio stream found in MPEG-TS".to_string())
    })?;

    let audio_data = pid_payloads.get(&audio_pid).ok_or_else(|| {
        AppError::Audio("Audio PID data not found".to_string())
    })?;

    let _ = (|| {
        use std::io::Write;
        let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
        writeln!(f, "Audio PID={:04X}, audio_data_len={}", audio_pid, audio_data.len()).ok()?;
        Some(())
    })();

    // Extract ADTS from PES payloads
    let mut adts_data = Vec::new();
    let mut offset = 0;
    let mut pes_count = 0;

    while offset < audio_data.len() {
        if offset + 6 > audio_data.len() {
            break;
        }

        // Look for PES start code: 0x00 0x00 0x01
        if audio_data[offset] == 0x00
            && audio_data[offset + 1] == 0x00
            && audio_data[offset + 2] == 0x01
        {
            let _stream_id = audio_data[offset + 3];
            // PES_packet_length (bytes 4-5), can be 0 for undefined length
            let pes_packet_length = if audio_data.len() >= offset + 6 {
                ((audio_data[offset + 4] as usize) << 8) | (audio_data[offset + 5] as usize)
            } else {
                0
            };

            let pes_header_length = if audio_data.len() > offset + 8 {
                9 + audio_data[offset + 8] as usize
            } else {
                6
            };

            let payload_start = offset + pes_header_length;
            if payload_start >= audio_data.len() {
                break;
            }

            let payload_end = if pes_packet_length > 0 && offset + 6 + pes_packet_length <= audio_data.len() {
                // Use PES_packet_length if defined and valid
                offset + 6 + pes_packet_length
            } else {
                // Find next PES start code or end of data
                let mut end = payload_start + 1;
                while end + 3 <= audio_data.len() {
                    if audio_data[end] == 0x00
                        && audio_data[end + 1] == 0x00
                        && audio_data[end + 2] == 0x01
                        && (audio_data[end + 3] >= 0xC0 && audio_data[end + 3] <= 0xDF)
                    {
                        break;
                    }
                    end += 1;
                }
                end
            };

            let payload = &audio_data[payload_start..payload_end.min(audio_data.len())];
            adts_data.extend_from_slice(payload);
            pes_count += 1;

            offset = payload_end;
        } else {
            offset += 1;
        }
    }

    let _ = (|| {
        use std::io::Write;
        let mut f = std::fs::OpenOptions::new().append(true).create(true).open("/tmp/ts_demux.log").ok()?;
        writeln!(f, "Extracted {} PES payloads, ADTS data len={}", pes_count, adts_data.len()).ok()?;
        Some(())
    })();

    if adts_data.is_empty() {
        return Err(AppError::Audio(
            "No ADTS data extracted from MPEG-TS".to_string(),
        ));
    }

    Ok(adts_data)
}

pub fn sampling_rate_from_index(index: u8) -> u32 {
    match index {
        0x0 => 96000,
        0x1 => 88200,
        0x2 => 64000,
        0x3 => 48000,
        0x4 => 44100,
        0x5 => 32000,
        0x6 => 24000,
        0x7 => 22050,
        0x8 => 16000,
        0x9 => 12000,
        0xA => 11025,
        0xB => 8000,
        0xC => 7350,
        _ => 44100,
    }
}

pub fn channels_count_from_config(config: u8) -> u32 {
    match config {
        1 => 1,
        2 => 2,
        3 => 3,
        4 => 4,
        5 => 5,
        6 => 6,
        7 => 8,
        _ => 2,
    }
}

pub fn get_first_adts_info(data: &[u8]) -> Option<AdtsInfo> {
    let mut offset = 0;
    while offset + 7 <= data.len() {
        if let Some((_, info)) = parse_adts_header(&data[offset..]) {
            return Some(info);
        }
        offset += 1;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_adts_header_valid() {
        // Create a minimal ADTS header for AAC-LC, 44100Hz, stereo
        // syncword: 0xFFF
        // ID: 1, layer: 0, protection_absent: 1
        // profile: 1 (AAC LC), sampling_freq_index: 4 (44100), channel_config: 2 (stereo)
        // frame_length: 100
        let data = vec![
            0xFF, 0xF9, // syncword(0xFFF) + ID(1) + layer(0) + protection_absent(1)
            0x50,       // profile(1) + sampling_freq_index(4) + channel_config_msb(0)
            0x80,       // channel_config_lsb(2<<6) + frame_length_msb(0)
            0x0C,       // frame_length_mid(12)
            0x80,       // frame_length_lsb(4<<5) + buffer_fullness_msb(0)
            0x00,       // buffer_fullness(0) + number_of_raw_data_blocks(0)
        ];

        let result = parse_adts_header(&data);
        assert!(result.is_some());
        let (frame_length, info) = result.unwrap();
        assert_eq!(frame_length, 100);
        assert_eq!(info.profile, 1);
        assert_eq!(info.sampling_freq_index, 4);
        assert_eq!(info.channel_config, 2);
        assert_eq!(info.header_size, 7);
    }

    #[test]
    fn test_parse_adts_header_invalid_syncword() {
        let data = vec![0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
        assert!(parse_adts_header(&data).is_none());
    }

    #[test]
    fn test_sampling_rate_from_index() {
        assert_eq!(sampling_rate_from_index(4), 44100);
        assert_eq!(sampling_rate_from_index(3), 48000);
        assert_eq!(sampling_rate_from_index(0xF), 44100); // default
    }
}
