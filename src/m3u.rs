use crate::error::{AppError, Result};

#[derive(Debug, Clone)]
pub struct Station {
    pub name: String,
    pub url: String,
}

pub fn parse_m3u(content: &str) -> Result<Vec<Station>> {
    let mut stations = Vec::new();
    let mut current_name: Option<String> = None;

    for line in content.lines() {
        let line = line.trim();

        if line.starts_with("#EXTINF:") {
            if let Some(comma_pos) = line.rfind(',') {
                current_name = Some(line[comma_pos + 1..].to_string());
            }
        } else if !line.is_empty() && !line.starts_with('#') {
            let name = current_name.take().unwrap_or_else(|| {
                line.split('/').next_back()
                    .unwrap_or("Unknown")
                    .trim_end_matches(".m3u8")
                    .to_string()
            });

            stations.push(Station {
                name,
                url: line.to_string(),
            });
        }
    }

    if stations.is_empty() {
        return Err(AppError::M3uParse("No stations found in M3U file".to_string()));
    }

    Ok(stations)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_valid_m3u() {
        let content = r#"#EXTM3U

#EXTINF:-1,中国之声
http://example.com/zgzs/index.m3u8

#EXTINF:-1,经济之声
http://example.com/jjzs/index.m3u8"#;

        let stations = parse_m3u(content).unwrap();
        assert_eq!(stations.len(), 2);
        assert_eq!(stations[0].name, "中国之声");
        assert_eq!(stations[0].url, "http://example.com/zgzs/index.m3u8");
        assert_eq!(stations[1].name, "经济之声");
    }

    #[test]
    fn test_parse_empty_file() {
        let content = "#EXTM3U\n";
        let result = parse_m3u(content);
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_no_extinf() {
        let content = "http://example.com/stream.m3u8\n";
        let stations = parse_m3u(content).unwrap();
        assert_eq!(stations.len(), 1);
        assert_eq!(stations[0].name, "stream");
    }
}