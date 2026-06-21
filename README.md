# Terminal Radio

Terminal Radio 是一个极简的 Android 网络电台播放器，内置中央人民广播电台 CNR 频道列表，支持 HLS/m3u8 播放、后台播放、锁屏控制、自定义 m3u 导入和自动恢复播放。

## 功能

- 启动后自动加载内置 `cnr.m3u`，默认播放“音乐之声”。
- 展示内置 CNR 电台列表，可点击切换电台。
- 支持导入本地 `.m3u` 文件作为自定义播放列表。
- 支持一键恢复内置电台列表。
- 支持播放、暂停、停止、重新连接、上一台、下一台。
- 使用 MediaSession 和媒体样式通知，支持通知栏、锁屏页面、耳机/车机媒体控制。
- 使用 Foreground Service 后台播放，锁屏、息屏、切到后台后继续播放。
- 断开耳机或蓝牙音频设备时自动暂停，避免外放。
- 网络恢复后自动尝试重播。
- 播放错误后自动延迟重连。
- WatchDog 定时检查播放状态，非用户暂停时自动恢复播放。
- 支持 Audio Focus：短暂失焦暂停，恢复焦点后继续播放。
- 深色播放器仪表盘 UI，动态展示播放状态、网络状态、播放时长、列表来源、电台名称和 URL。
- 支持 Android Auto 媒体应用声明。

## 当前内置电台

内置列表来自 `cnr.m3u`，包含 11 个 CNR 频道：

| 频道 | 流地址 |
| --- | --- |
| 中国之声 | `http://ngcdn001.cnr.cn/live/zgzs/index.m3u8` |
| 经济之声 | `http://ngcdn001.cnr.cn/live/jjzs/index.m3u8` |
| 音乐之声 | `http://ngcdn001.cnr.cn/live/yyzs/index.m3u8` |
| 文艺之声 | `http://ngcdn001.cnr.cn/live/wyzs/index.m3u8` |
| 环球资讯广播 | `http://ngcdn002.cnr.cn/live/hqzx/index.m3u8` |
| 轻松调频 | `http://ngcdn002.cnr.cn/live/qsdt/index.m3u8` |
| 南海之声 | `http://ngcdn002.cnr.cn/live/nhzs/index.m3u8` |
| 神州之声 | `http://ngcdn002.cnr.cn/live/szzs/index.m3u8` |
| 华夏之声 | `http://ngcdn002.cnr.cn/live/hxzs/index.m3u8` |
| 民族之声 | `http://ngcdn002.cnr.cn/live/mzzs/index.m3u8` |
| 香港之声 | `http://ngcdn002.cnr.cn/live/xgzs/index.m3u8` |

## 使用方法

### 播放电台

1. 打开 App 后会自动加载播放列表并播放“音乐之声”。
2. 在播放列表中点击任意电台即可切换播放。
3. 使用页面中间的控制按钮执行播放、暂停、停止、重连、上一台、下一台。
4. 锁屏或下拉通知栏后，可使用系统媒体控制按钮控制播放。

### 导入自定义 m3u

1. 点击“导入 m3u”。
2. 从系统文件选择器选择本地 `.m3u` 文件。
3. App 会解析文件并切换到自定义列表。
4. 点击“恢复内置”可切回内置 CNR 列表。

自定义 m3u 文件需包含可播放的 HLS 或音频 URL。推荐格式：

```m3u
#EXTM3U
#EXTINF:-1,示例电台
https://example.com/live/index.m3u8
```

### 后台和锁屏播放

- 播放时 App 会通过前台媒体服务维持后台播放。
- 锁屏页面会显示系统媒体控制按钮。
- 如果耳机或蓝牙音频设备断开，播放会自动暂停。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: Activity + ViewModel + Foreground `MediaSessionService` + 播放管理器
- **播放器**: AndroidX Media3 ExoPlayer + HLS
- **媒体控制**: MediaSession + Notification MediaStyle
- **异步**: Kotlin Coroutines + StateFlow
- **最小 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **构建工具**: Gradle 8.2

## 权限

App 使用以下权限：

- `INTERNET`: 播放网络电台流。
- `ACCESS_NETWORK_STATE`: 监听网络状态并在恢复后自动重播。
- `FOREGROUND_SERVICE`: 运行前台播放服务。
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Android 14+ 媒体播放前台服务类型。
- `WAKE_LOCK`: 帮助维持播放过程稳定。
- `POST_NOTIFICATIONS`: Android 13+ 显示媒体播放通知。
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: 保留权限声明，但当前启动时不会主动弹出电池优化设置。

## 项目结构

```text
.
├── android/
│   ├── app/
│   │   ├── src/main/assets/
│   │   │   └── cnr.m3u
│   │   ├── src/main/java/co/terminal/radio/
│   │   │   ├── MainActivity.kt              # Compose 入口、权限、m3u 文件选择
│   │   │   ├── RadioScreen.kt               # 播放器仪表盘 UI
│   │   │   ├── RadioViewModel.kt            # UI 状态和控制入口
│   │   │   ├── RadioControlRepository.kt    # 向播放服务发送控制动作
│   │   │   ├── RadioPlaybackService.kt      # Foreground MediaSessionService
│   │   │   ├── RadioPlayerManager.kt        # ExoPlayer、重连、焦点、列表管理
│   │   │   ├── PlaybackModels.kt            # 播放状态、动作、UI 状态模型
│   │   │   ├── NetworkMonitor.kt            # 网络监听
│   │   │   ├── M3uParser.kt                 # M3U 解析
│   │   │   └── RadioApplication.kt          # Application 入口
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── cnr.m3u                                # 根目录内置列表副本
├── Dockerfile.android                     # Docker Android 构建环境
├── Makefile                               # 构建命令
└── README.md
```

## 构建

推荐使用 Makefile 中的命令。

### Docker 构建

```bash
make docker
```

Docker 构建会自动创建 Android 构建环境并输出 APK 到项目根目录，文件名格式为：

```text
TerminalRadio-v<versionName>.apk
```

例如当前版本会输出：

```text
TerminalRadio-v1.2.8.apk
```

### 本地 Gradle 构建

本地需要 Android SDK 和 JDK 17：

```bash
make build
```

也可以用 Android Studio 打开 `android/` 目录后构建。

## 安装

连接 Android 手机并开启 USB 调试后执行：

```bash
adb install TerminalRadio-v1.2.8.apk
```

如需覆盖安装：

```bash
adb install -r TerminalRadio-v1.2.8.apk
```

## 开发说明

- 每次修改代码或配置后，需要同步升级 `android/app/build.gradle.kts` 中的版本信息。
- 优先使用 `make docker` 验证构建。
- 内置列表源文件位于 `android/app/src/main/assets/cnr.m3u`。
- 根目录 `cnr.m3u` 用于查看和同步内置列表内容。

## License

MIT
