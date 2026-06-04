# Terminal Radio

一个基于终端的 TUI 网络电台播放器，支持 HLS/M3U 格式，使用 Rust 编写。

## 功能特性

- **TUI 界面**：基于 Ratatui 的终端用户界面，简洁美观
- **HLS 流播放**：支持 HLS (HTTP Live Streaming) 格式的网络电台
- **M3U 列表**：从 M3U 文件加载电台列表
- **默认播放**：启动后自动播放"音乐之声"
- **蓝牙耳机友好**：蓝牙设备断开时不会暂停，重新连接后自动恢复播放
- **音量控制**：支持实时音量调节（`+` / `-`）
- **收藏功能**：按 `f` 键收藏/取消收藏喜欢的电台
- **键盘操作**：
  - `↑` / `↓` 或 `k` / `j`：选择电台
  - `Enter`：播放选中的电台
  - `Space`：暂停/恢复播放
  - `+` / `-`：音量增/减
  - `f`：收藏/取消收藏
  - `q` / `Esc` / `Ctrl+C`：退出程序

## 安装

### 前提条件

- Rust 1.70+
- macOS / Linux / Windows

### 从源码编译

```bash
git clone https://github.com/yourusername/terminal-radio
cd terminal-radio
cargo build --release
```

编译完成后，可执行文件位于 `target/release/terminal-radio`。

### 快速运行

```bash
cargo run
```

或：

```bash
make run
```

## 使用方法

### 电台列表

程序默认从 `cnr.m3u` 文件加载电台列表。该文件已预置了多个央广电台：

- 音乐之声（默认播放）
- 环球资讯广播
- 轻松调频
- 南海之声
- 神州之声
- 华夏之声
- 民族之声
- 香港之声

### 添加电台

编辑 `cnr.m3u` 文件，按照 M3U 格式添加新的电台：

```m3u
#EXTINF:-1,电台名称
http://电台流地址.m3u8
```

## 技术栈

- **TUI 框架**：[ratatui](https://github.com/ratatui/ratatui) + [crossterm](https://github.com/crossterm-rs/crossterm)
- **音频播放**：[cpal](https://github.com/RustAudio/cpal)（跨平台音频输出）+ [symphonia](https://github.com/pdeljanov/Symphonia)（音频解码）
- **网络请求**：[reqwest](https://github.com/seanmonstar/reqwest)
- **配置持久化**：[serde](https://serde.rs/) + JSON

## 项目结构

```
.
├── src/
│   ├── main.rs         # 程序入口
│   ├── app.rs          # 应用状态管理
│   ├── ui.rs           # TUI 渲染
│   ├── event.rs        # 键盘事件处理
│   ├── player.rs       # 音频播放（CPAL + Symphonia）
│   ├── hls.rs          # HLS 播放列表解析
│   ├── hls_stream.rs   # HLS 流下载与播放
│   ├── ts_demux.rs     # MPEG-TS 解封装
│   ├── m3u.rs          # M3U 文件解析
│   ├── config.rs       # 配置管理（音量、收藏）
│   └── error.rs        # 错误类型定义
├── cnr.m3u             # 默认电台列表
└── Cargo.toml
```

## 注意事项

1. **音频格式**：部分 HLS 流使用 MPEG-TS 容器封装 AAC 音频，程序内置了 MPEG-TS 解封装支持
2. **网络依赖**：播放需要稳定的网络连接
3. **日志**：默认禁用日志输出，避免干扰 TUI 界面。如需调试，可设置 `RUST_LOG` 环境变量

## 许可证

MIT
