use thiserror::Error;

#[derive(Error, Debug)]
pub enum AppError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("HTTP error: {0}")]
    Http(#[from] reqwest::Error),

    #[error("M3U parse error: {0}")]
    M3uParse(String),

    #[error("HLS error: {0}")]
    Hls(String),

    #[error("Audio error: {0}")]
    Audio(String),

    #[error("Config error: {0}")]
    Config(String),
}

pub type Result<T> = std::result::Result<T, AppError>;