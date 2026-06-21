# Lockscreen Station List Custom M3U Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android lock-screen media controls, a selectable station list, local custom M3U import, and Bluetooth disconnect pause behavior.

**Architecture:** Keep the existing single Android app module. `RadioPlayerManager` owns station loading, source switching, selection, previous/next, and safety pause. `RadioPlaybackService` owns foreground media notification actions. `MainActivity` owns the file picker and forwards selected file contents through `RadioViewModel` and `RadioControlRepository`.

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer/MediaSession, AndroidX NotificationCompat MediaStyle, Activity Result API, Coroutines/StateFlow.

---

### Task 1: Dependencies and Version

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] Bump `versionCode` from `11` to `12` and `versionName` from `1.2.4` to `1.2.5`.
- [ ] Add `implementation("androidx.media:media:1.7.0")` for `NotificationCompat.MediaStyle`.

### Task 2: Playback Models and Actions

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/PlaybackModels.kt`

- [ ] Extend `PlaybackUiState` with `stations: List<Station> = emptyList()`, `selectedStationUrl: String = ""`, and `sourceName: String = "内置列表"`.
- [ ] Add service actions: `ACTION_SELECT_STATION`, `ACTION_PREVIOUS`, `ACTION_NEXT`, `ACTION_IMPORT_M3U`, `ACTION_RESTORE_BUILT_IN`.
- [ ] Add extras: `EXTRA_STATION_URL`, `EXTRA_M3U_CONTENT`.

### Task 3: Playback Manager Station Sources

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/RadioPlayerManager.kt`

- [ ] Add station list state and app-private custom M3U storage helpers.
- [ ] Load custom M3U content first if present; otherwise load bundled `cnr.m3u`.
- [ ] Publish stations, selected station URL, and source name in `PlaybackUiState`.
- [ ] Add `selectStation(url)`, `playPrevious()`, `playNext()`, `importM3u(rawContent)`, and `restoreBuiltInStations()`.
- [ ] Keep `音乐之声` as default selection when present.

### Task 4: Bluetooth and Noisy Pause

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/RadioPlayerManager.kt`

- [ ] Register a receiver for `ACTION_AUDIO_BECOMING_NOISY` and Bluetooth ACL disconnect.
- [ ] Replace noisy pause with `safetyPause()` that sets `userPaused = true` before pausing.
- [ ] Use `ContextCompat.registerReceiver` flags for Android 13+ dynamic receiver safety.

### Task 5: Media Notification Controls

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/RadioPlaybackService.kt`

- [ ] Handle previous, next, import, restore, and select station intents.
- [ ] Build notification with `androidx.media.app.NotificationCompat.MediaStyle` and `mediaSession.sessionCompatToken`.
- [ ] Show compact lock-screen actions: previous, play/pause, next.
- [ ] Keep reconnect and stop as expanded notification actions.

### Task 6: Repository and ViewModel Actions

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/RadioControlRepository.kt`
- Modify: `android/app/src/main/java/co/terminal/radio/RadioViewModel.kt`

- [ ] Add `selectStation(url)`, `previous()`, `next()`, `importM3u(rawContent)`, and `restoreBuiltInStations()`.
- [ ] Send import/select extras via explicit service intents.
- [ ] Treat start/play/select/import/restore as foreground-start actions because they can start playback or refresh active state.

### Task 7: File Picker and Compose UI

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/MainActivity.kt`
- Modify: `android/app/src/main/java/co/terminal/radio/RadioScreen.kt`

- [ ] Add an Activity Result file picker using `GetContent()` with MIME `*/*`.
- [ ] Read selected file text and pass it to `viewModel.importM3u`.
- [ ] Add source controls and station list to `RadioScreen`.
- [ ] Wire previous/next/select/import/restore callbacks.

### Task 8: Verification

**Files:**
- Build output: `TerminalRadio-v1.2.5.apk`

- [ ] Run `make docker`.
- [ ] Confirm the APK is generated.
- [ ] If build fails, fix root cause and rerun `make docker`.
