use std::path::PathBuf;
use serde::{Deserialize, Serialize};
use crate::error::{AppError, Result};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub volume: f32,
    pub favorites: Vec<String>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            volume: 0.8,
            favorites: Vec::new(),
        }
    }
}

impl Config {
    pub fn load() -> Result<Self> {
        let path = Self::config_path()?;
        if path.exists() {
            let content = std::fs::read_to_string(&path)
                .map_err(|e| AppError::Config(format!("Failed to read config: {}", e)))?;
            toml::from_str(&content)
                .map_err(|e| AppError::Config(format!("Failed to parse config: {}", e)))
        } else {
            Ok(Self::default())
        }
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::config_path()?;
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)
                .map_err(|e| AppError::Config(format!("Failed to create config dir: {}", e)))?;
        }
        let content = toml::to_string_pretty(self)
            .map_err(|e| AppError::Config(format!("Failed to serialize config: {}", e)))?;
        std::fs::write(&path, content)
            .map_err(|e| AppError::Config(format!("Failed to write config: {}", e)))?;
        Ok(())
    }

    pub fn toggle_favorite(&mut self, station_name: &str) {
        if let Some(pos) = self.favorites.iter().position(|x| x == station_name) {
            self.favorites.remove(pos);
        } else {
            self.favorites.push(station_name.to_string());
        }
    }

    pub fn is_favorite(&self, station_name: &str) -> bool {
        self.favorites.contains(&station_name.to_string())
    }

    fn config_path() -> Result<PathBuf> {
        let config_dir = dirs::config_dir()
            .ok_or_else(|| AppError::Config("Could not find config directory".to_string()))?;
        Ok(config_dir.join("terminal-radio").join("config.toml"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_toggle_favorite() {
        let mut config = Config::default();
        assert!(!config.is_favorite("test"));
        
        config.toggle_favorite("test");
        assert!(config.is_favorite("test"));
        
        config.toggle_favorite("test");
        assert!(!config.is_favorite("test"));
    }

    #[test]
    fn test_default_config() {
        let config = Config::default();
        assert_eq!(config.volume, 0.8);
        assert!(config.favorites.is_empty());
    }
}