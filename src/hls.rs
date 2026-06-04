use crate::error::{AppError, Result};
use reqwest::blocking::Client;

#[derive(Debug, Clone)]
pub struct HlsSegment {
    pub url: String,
    #[allow(dead_code)]
    pub duration: Option<f64>,
}

#[derive(Debug)]
pub struct HlsPlaylist {
    pub segments: Vec<HlsSegment>,
    pub target_duration: Option<f64>,
}

pub fn parse_hls_playlist(content: &str, base_url: &str) -> Result<HlsPlaylist> {
    let mut segments = Vec::new();
    let mut target_duration = None;
    let mut current_duration: Option<f64> = None;

    for line in content.lines() {
        let line = line.trim();

        if line.starts_with("#EXT-X-TARGETDURATION:") {
            if let Ok(dur) = line.trim_start_matches("#EXT-X-TARGETDURATION:").parse::<f64>() {
                target_duration = Some(dur);
            }
        } else if line.starts_with("#EXTINF:") {
            let info = line.trim_start_matches("#EXTINF:");
            if let Some(comma_pos) = info.find(',') {
                if let Ok(dur) = info[..comma_pos].parse::<f64>() {
                    current_duration = Some(dur);
                }
            }
        } else if !line.is_empty() && !line.starts_with('#') {
            let url = resolve_url(base_url, line);
            segments.push(HlsSegment {
                url,
                duration: current_duration.take(),
            });
        }
    }

    if segments.is_empty() {
        return Err(AppError::Hls("No segments found in HLS playlist".to_string()));
    }

    Ok(HlsPlaylist {
        segments,
        target_duration,
    })
}

fn resolve_url(base_url: &str, path: &str) -> String {
    if path.starts_with("http://") || path.starts_with("https://") {
        return path.to_string();
    }

    if let Some(base) = base_url.rfind('/') {
        format!("{}{}", &base_url[..base + 1], path)
    } else {
        path.to_string()
    }
}

pub fn fetch_hls_playlist(client: &Client, url: &str) -> Result<HlsPlaylist> {
    let response = client.get(url)
        .header("User-Agent", "Mozilla/5.0")
        .send()
        .map_err(|e| AppError::Hls(format!("Failed to fetch HLS playlist: {}", e)))?;

    let content = response.text()
        .map_err(|e| AppError::Hls(format!("Failed to read HLS playlist: {}", e)))?;

    parse_hls_playlist(&content, url)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_hls_playlist() {
        let content = r#"#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXTINF:9.009,
segment00001.ts
#EXTINF:9.009,
segment00002.ts
#EXTINF:3.003,
segment00003.ts"#;

        let playlist = parse_hls_playlist(content, "http://example.com/live/index.m3u8").unwrap();
        assert_eq!(playlist.segments.len(), 3);
        assert_eq!(playlist.segments[0].url, "http://example.com/live/segment00001.ts");
        assert_eq!(playlist.segments[0].duration, Some(9.009));
        assert_eq!(playlist.target_duration, Some(10.0));
    }

    #[test]
    fn test_resolve_url_absolute() {
        let url = resolve_url("http://example.com/live/index.m3u8", "http://other.com/segment.ts");
        assert_eq!(url, "http://other.com/segment.ts");
    }

    #[test]
    fn test_resolve_url_relative() {
        let url = resolve_url("http://example.com/live/index.m3u8", "segment.ts");
        assert_eq!(url, "http://example.com/live/segment.ts");
    }
}