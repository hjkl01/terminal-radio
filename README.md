# Terminal Radio

Terminal Radio 是一个极简的 Android 网络电台播放器，内置中央人民广播电台 CNR 频道列表，支持 HLS/m3u8 播放、后台播放、锁屏控制、自定义 m3u 导入和自动恢复播放。

项目使用 Kotlin + Jetpack Compose + AndroidX Media3 构建。

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

内置列表来自 `cnr.m3u`，包含 11 个 CNR 频道：中国之声、经济之声、音乐之声、文艺之声、环球资讯广播、轻松调频、南海之声、神州之声、华夏之声、民族之声、香港之声。

## 使用方法

### 播放电台

1. 安装 APK 并打开 App。
2. App 会自动加载内置播放列表并尝试播放“音乐之声”。
3. 在播放列表中点击任意电台即可切换。
4. 使用播放器控制按钮执行播放、暂停、停止、重连、上一台、下一台。
5. 锁屏或下拉通知栏后，可使用系统媒体控制按钮控制播放。

### 导入自定义 m3u

1. 点击“导入 m3u”。
2. 从系统文件选择器选择本地 `.m3u` 文件。
3. App 会解析文件并切换到自定义列表。
4. 点击“恢复内置”可切回内置 CNR 列表。

推荐格式：

```m3u
#EXTM3U
#EXTINF:-1,示例电台
https://example.com/live/index.m3u8
```

### 后台和锁屏播放

- 播放时 App 会通过前台媒体服务维持后台播放。
- 锁屏页面会显示系统媒体控制按钮。
- 如果耳机或蓝牙音频设备断开，播放会自动暂停。
- 网络恢复后会自动尝试恢复播放。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: Activity + ViewModel + Foreground `MediaSessionService` + 播放管理器
- **播放器**: AndroidX Media3 ExoPlayer + HLS
- **媒体控制**: MediaSession + Notification MediaStyle
- **异步**: Kotlin Coroutines + StateFlow
- **最小 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **Gradle**: 8.2
- **JDK**: 17

## 项目结构

```text
.
├── android/
│   ├── app/
│   │   ├── src/main/assets/cnr.m3u
│   │   ├── src/main/java/co/terminal/radio/
│   │   │   ├── MainActivity.kt
│   │   │   ├── RadioScreen.kt
│   │   │   ├── RadioViewModel.kt
│   │   │   ├── RadioControlRepository.kt
│   │   │   ├── RadioPlaybackService.kt
│   │   │   ├── RadioPlayerManager.kt
│   │   │   ├── PlaybackModels.kt
│   │   │   ├── NetworkMonitor.kt
│   │   │   ├── M3uParser.kt
│   │   │   └── RadioApplication.kt
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── cnr.m3u
├── Dockerfile.android
├── Makefile
└── README.md
```

## 构建 APK

项目提供 Docker 和本地 Gradle 两种构建方式。

### Docker 构建（推荐）

只需要安装 Docker，不要求宿主机安装 Gradle、Android SDK 或 JDK：

```bash
make docker
```

`Dockerfile.android` 会自动准备 JDK 17、Android SDK Command-line Tools、Android Platform 34、Build Tools 33.0.0 和 Gradle 8.2。因此宿主机完全没有 Gradle 也可以直接构建。

构建完成后，APK 会复制到项目根目录：

```text
TerminalRadio-v<versionName>.apk
```

例如：

```text
TerminalRadio-v1.2.8.apk
```

### 本地 Gradle 构建

本地只需要准备 Android SDK 和 JDK 17：

```bash
make build
```

也可以直接：

```bash
cd android
./gradlew assembleDebug
```

**不需要预先安装 Gradle。** 项目自带 Gradle Wrapper，版本由 `android/gradle/wrapper/gradle-wrapper.properties` 固定为 Gradle 8.2；如果本机没有对应 Gradle 分发包，Wrapper 会自动从 Gradle 官方地址下载并缓存。首次下载后，后续构建会复用缓存。

如果使用 Android Studio，也可以直接打开 `android/` 目录进行构建。

### 构建产物

Gradle 原始产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Makefile 会复制并重命名为：

```text
TerminalRadio-v<versionName>.apk
```

## 安装 APK

### ADB 安装

连接 Android 手机并开启 USB 调试：

```bash
adb devices
adb install TerminalRadio-v1.2.8.apk
```

覆盖安装并保留应用数据：

```bash
adb install -r TerminalRadio-v1.2.8.apk
```

### 手动安装

将 `TerminalRadio-v<versionName>.apk` 复制到 Android 手机，在文件管理器中点击安装即可。如果系统禁止当前应用安装未知来源应用，需要在系统设置中允许该权限。

## 开发说明

修改代码后推荐使用：

```bash
make docker
```

这样可以使用项目固定的 JDK、Android SDK 和 Gradle 环境，减少本机环境差异导致的构建问题。

本地开发可以使用：

```bash
make build
```

每次发布新版本时，需要同步修改 `android/app/build.gradle.kts` 中的 `versionName` 和 `versionCode`。

内置电台列表源文件位于：

```text
android/app/src/main/assets/cnr.m3u
```

根目录 `cnr.m3u` 用于查看和同步内置列表内容。

## Makefile 命令

| 命令 | 作用 |
| --- | --- |
| `make help` | 查看可用命令 |
| `make docker` | 使用 Docker 构建 APK，不依赖宿主机 Gradle |
| `make build` | 使用 Gradle Wrapper 本地构建 APK |

## 权限

- `INTERNET`: 播放网络电台流。
- `ACCESS_NETWORK_STATE`: 监听网络状态并在恢复后自动重播。
- `FOREGROUND_SERVICE`: 运行前台播放服务。
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Android 14+ 媒体播放前台服务类型。
- `WAKE_LOCK`: 帮助维持播放过程稳定。
- `POST_NOTIFICATIONS`: Android 13+ 显示媒体播放通知。
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: 保留权限声明，但当前启动时不会主动弹出电池优化设置。

## License

MIT
