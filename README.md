# Terminal Radio

一个轻量级的中国之声电台 Android 应用，支持 HLS 流媒体播放。

## 功能

- 收听中国之声、经济之声、音乐之声等 11 个中央人民广播电台频道
- HLS (m3u8) 流媒体播放
- 播放/暂停、上一曲/下一曲控制
- 通知栏媒体控制
- 支持后台播放

## 技术栈

- **语言**: Kotlin
- **最小 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **播放器**: ExoPlayer (Media3)
- **构建工具**: Gradle 8.2
- **UI**: ViewBinding + RecyclerView

## 快速开始

### 环境要求

- Android Studio (推荐)
- JDK 17+
- Gradle 8.2+

### 本地构建

```bash
# 使用 Gradle 直接构建
make android

# 或使用 Android Studio
# 打开 android/ 目录，点击 Build -> Build APK
```

### Docker 构建

```bash
# 使用 Docker 一键构建 APK
make docker
```

构建完成后 APK 会输出到项目根目录：`app-debug.apk`

### 安装到设备

```bash
# 连接手机并开启 USB 调试
adb install app-debug.apk
```

## 项目结构

```
.
├── android/                    # Android 项目
│   ├── app/
│   │   ├── src/main/java/co/terminal/radio/
│   │   │   ├── MainActivity.kt       # 主界面
│   │   │   ├── RadioApplication.kt   # 播放器管理
│   │   │   ├── RadioReceiver.kt      # 通知栏广播接收器
│   │   │   ├── StationAdapter.kt     # 电台列表适配器
│   │   │   ├── M3uParser.kt          # M3U 解析器
│   │   │   └── AppConfig.kt          # 应用配置
│   │   ├── src/main/assets/
│   │   │   └── cnr.m3u               # 电台列表 (中央人民广播)
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── cnr.m3u                   # 电台列表 (根目录副本)
├── Dockerfile.android        # Docker 构建配置
├── Makefile                  # 构建命令
└── README.md
```

## 电台列表

| 频道 | 流地址 |
|------|--------|
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

电台列表可通过编辑 `android/app/src/main/assets/cnr.m3u` 自定义。

## 构建说明

### 使用 Makefile

```bash
make help          # 查看所有可用命令
make docker        # Docker 构建 APK
make android       # 本地 Gradle 构建
```

### Docker 构建

Docker 镜像预装了 Android SDK 和 Gradle，无需本地安装 Android Studio 即可构建 APK。

```bash
# 构建并运行
make docker
```

## License

MIT
