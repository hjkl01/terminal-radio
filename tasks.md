---

# 需求说明

请帮我开发一个 Android App（Kotlin + Jetpack Compose），用于播放我提供的 M3U8 电台流媒体。

## 核心功能

这是一个极简 App，主要运行场景：

* 手机连接车机（Android Auto 或投屏）
* 用户开车时持续收听网络电台
* App 打开后自动开始播放
* 尽可能保持持续播放

## 技术要求

### 播放器

使用：

* Android Kotlin
* Jetpack Compose
* AndroidX Media3 ExoPlayer

不要使用废弃 API。

### 播放源

./cnr.m3u

启动时自动加载该地址 并自动播放 音乐之声。

---

## 自动播放

App启动后：

1. 自动初始化播放器
2. 自动开始播放
3. 不需要用户主动点击播放按钮

---

## 后台播放

必须支持：

* App退到后台继续播放
* 锁屏继续播放
* 手机息屏继续播放

实现：

* Foreground Service
* MediaSession
* Notification控制

---

## 自动重连

网络电台可能出现：

* 网络波动
* M3U8断流
* HTTP错误
* ExoPlayer播放失败

要求：

### 播放失败

监听：

```kotlin
Player.Listener
```

当收到：

```kotlin
onPlayerError()
```

时：

* 延迟3秒
* 自动重新连接

---

### 网络恢复

监听：

```kotlin
ConnectivityManager
```

当网络恢复时：

* 自动重新播放

---

### 被系统暂停

监听：

* AudioFocus
* Audio Becoming Noisy

当焦点恢复后：

* 自动恢复播放

---

## 被其他App打断

场景：

* 微信语音
* 电话
* 导航播报
* 其他播放器

要求：

### 短暂失焦

如果收到：

```kotlin
AUDIOFOCUS_LOSS_TRANSIENT
```

暂停

收到：

```kotlin
AUDIOFOCUS_GAIN
```

立即恢复播放

---

### 永久失焦

如果收到：

```kotlin
AUDIOFOCUS_LOSS
```

记录状态

每隔10秒检查：

```kotlin
player.isPlaying
```

如果播放器停止且没有用户主动暂停：

尝试重新申请 Audio Focus 并恢复播放。

---

## 防止意外停止

增加 WatchDog：

每30秒检查一次：

```kotlin
player.playWhenReady
player.isPlaying
```

如果发现：

```kotlin
!player.isPlaying
```

则：

```kotlin
player.prepare()
player.play()
```

自动恢复。

---

## 用户界面

界面极简。

显示：

* 当前播放状态
* 当前URL
* 网络状态
* 播放时长

按钮：

* 播放
* 暂停
* 停止
* 重新连接

采用 Material 3 风格。

---

## 权限

申请：

```xml
INTERNET
FOREGROUND_SERVICE
WAKE_LOCK
ACCESS_NETWORK_STATE
FOREGROUND_SERVICE_MEDIA_PLAYBACK
```

必要时保持：

```xml
android.permission.POST_NOTIFICATIONS
```

兼容 Android 13+

---

## 电池优化

增加引导：

```kotlin
ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```

提示用户将 App 加入电池优化白名单。

---

## Android Auto

兼容 Android Auto。

即使界面非常简单，也需要：

* MediaSession
* 媒体通知
* 车机显示播放状态

---

## 项目要求

生成完整项目：

* Gradle配置
* AndroidManifest.xml
* MainActivity
* ForegroundService
* ExoPlayer管理类
* AudioFocus管理
* Network监听
* WatchDog实现
* Compose UI

要求：

* 可以直接在 Android Studio 最新版本打开
* 编译通过
* 无明显警告
* 代码结构清晰
* 使用 MVVM
* 每个文件给出完整代码
* 不要省略任何关键实现

最后给出：

1. 项目目录结构
2. 完整源代码
3. 构建步骤
4. APK打包步骤
5. 操作命令都依赖Makefile实现
6. 要求打包的app可以安装在普通的ARM手机上

---

> 这是一个长期后台播放网络电台的应用，请尽量参考 Spotify、Pocket Casts、RadioDroid 等音频应用的实现方式，优先保证播放稳定性，而不是界面复杂度。对于 Android 12～16 的后台限制、前台服务限制、Doze、省电策略、Audio Focus 竞争等情况，尽可能提供可靠的恢复机制。

