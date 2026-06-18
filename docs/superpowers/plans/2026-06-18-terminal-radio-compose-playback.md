# Terminal Radio Compose Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the app as a Kotlin + Jetpack Compose + Media3 radio player that auto-plays `音乐之声` and survives background playback, network interruptions, and audio-focus competition.

**Architecture:** Keep the current single Android module. Move playback ownership from `RadioApplication` into a foreground-capable `MediaSessionService`, expose state through a small repository/ViewModel boundary, and render the app with Compose Material 3.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose Material 3, AndroidX Lifecycle ViewModel, Kotlin Coroutines/StateFlow, AndroidX Media3 ExoPlayer, Media3 Session, Android foreground service APIs.

---

## File Structure

- Modify `android/app/build.gradle.kts`: enable Compose, bump version, update dependencies.
- Modify `android/app/src/main/AndroidManifest.xml`: permissions, Compose activity theme, media playback service, Android Auto media browser metadata.
- Modify `android/app/src/main/java/co/terminal/radio/MainActivity.kt`: replace AppCompat XML logic with Compose UI, permissions, battery optimization prompt, ViewModel actions.
- Modify `android/app/src/main/java/co/terminal/radio/M3uParser.kt`: keep parser pure and remove unused imports.
- Modify `android/app/src/main/java/co/terminal/radio/RadioApplication.kt`: reduce to lightweight `Application` or remove playback responsibilities.
- Delete `android/app/src/main/java/co/terminal/radio/StationAdapter.kt`: no RecyclerView after Compose migration.
- Delete `android/app/src/main/java/co/terminal/radio/RadioReceiver.kt`: notification actions are handled through MediaSession.
- Add `android/app/src/main/java/co/terminal/radio/PlaybackModels.kt`: station, playback status, service action, UI state models.
- Add `android/app/src/main/java/co/terminal/radio/NetworkMonitor.kt`: `ConnectivityManager.NetworkCallback` wrapper.
- Add `android/app/src/main/java/co/terminal/radio/RadioPlayerManager.kt`: ExoPlayer setup, M3U loading, reconnect, watchdog, focus handling, noisy receiver.
- Add `android/app/src/main/java/co/terminal/radio/RadioPlaybackService.kt`: MediaSessionService and foreground playback entry point.
- Add `android/app/src/main/java/co/terminal/radio/RadioControlRepository.kt`: service command + state observation facade for ViewModel/UI.
- Add `android/app/src/main/java/co/terminal/radio/RadioViewModel.kt`: MVVM state holder and UI actions.
- Add `android/app/src/main/java/co/terminal/radio/RadioScreen.kt`: Material 3 Compose screen.
- Add `android/app/src/main/res/xml/automotive_app_desc.xml`: Android Auto media app metadata.
- Remove unused XML layouts if no generated binding references remain.

## Tasks

### Task 1: Update Gradle For Compose

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] Enable Compose by setting `buildFeatures { compose = true }` and `composeOptions { kotlinCompilerExtensionVersion = "1.5.4" }`.
- [ ] Bump `versionCode` from `6` to `7` and `versionName` from `1.1.5` to `1.2.0`.
- [ ] Remove AppCompat, Material Components, ConstraintLayout, Media3 UI, Gson, and ViewBinding-only dependencies when unused.
- [ ] Add `androidx.activity:activity-compose:1.8.2`, `androidx.compose:compose-bom:2024.02.00`, Compose Material 3, Compose UI tooling, `androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.7.0`, and Media3 ExoPlayer/session/HLS dependencies.

### Task 2: Add Shared Models

**Files:**
- Create: `android/app/src/main/java/co/terminal/radio/PlaybackModels.kt`
- Modify: `android/app/src/main/java/co/terminal/radio/M3uParser.kt`

- [ ] Move `Station` into `PlaybackModels.kt` so all layers share the same model.
- [ ] Add `PlaybackStatus`, `PlaybackUiState`, and service action constants.
- [ ] Keep `M3uParser.parse(rawContent: String): List<Station>` pure and remove unused Gson/Uri imports.

### Task 3: Add Network Monitoring

**Files:**
- Create: `android/app/src/main/java/co/terminal/radio/NetworkMonitor.kt`

- [ ] Implement a lifecycle-controlled `NetworkMonitor` using `ConnectivityManager.NetworkCallback`.
- [ ] Expose `StateFlow<Boolean>` for online state.
- [ ] Provide `start()` and `stop()` methods that safely register/unregister callbacks once.

### Task 4: Implement Playback Manager

**Files:**
- Create: `android/app/src/main/java/co/terminal/radio/RadioPlayerManager.kt`

- [ ] Build ExoPlayer with media audio attributes, wake mode, and HLS support through Media3.
- [ ] Load `cnr.m3u` from assets and select station named `音乐之声`, falling back to the first station.
- [ ] Add `Player.Listener` and retry `onPlayerError()` after 3 seconds when the user has not stopped playback.
- [ ] Add Audio Focus handling for transient loss, gain, ducking, and permanent loss retry every 10 seconds.
- [ ] Register `AudioManager.ACTION_AUDIO_BECOMING_NOISY` receiver and pause on noisy output changes.
- [ ] Add 30-second watchdog that restores playback if `!player.isPlaying` and the user did not pause/stop.
- [ ] Observe `NetworkMonitor` and reconnect when network returns.
- [ ] Publish `StateFlow<PlaybackUiState>` for UI and notification state.

### Task 5: Implement MediaSession Service

**Files:**
- Create: `android/app/src/main/java/co/terminal/radio/RadioPlaybackService.kt`
- Modify: `android/app/src/main/java/co/terminal/radio/RadioApplication.kt`

- [ ] Create `RadioPlaybackService : MediaSessionService`.
- [ ] Initialize `RadioPlayerManager` and `MediaSession` in service lifecycle.
- [ ] Handle service actions: start auto-play, play, pause, stop, reconnect.
- [ ] Return the session from `onGetSession()`.
- [ ] Release player, session, network monitor, receivers, and coroutines in `onDestroy()`.
- [ ] Keep `RadioApplication` free of ExoPlayer state.

### Task 6: Add Repository And ViewModel

**Files:**
- Create: `android/app/src/main/java/co/terminal/radio/RadioControlRepository.kt`
- Create: `android/app/src/main/java/co/terminal/radio/RadioViewModel.kt`

- [ ] Implement repository commands by starting `RadioPlaybackService` with explicit actions.
- [ ] Expose state from a process-level service state holder so Compose can observe playback status.
- [ ] Implement ViewModel actions: `startAutoPlay()`, `play()`, `pause()`, `stop()`, `reconnect()`.
- [ ] Keep ViewModel free of Android player implementation details.

### Task 7: Replace UI With Compose

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/MainActivity.kt`
- Create: `android/app/src/main/java/co/terminal/radio/RadioScreen.kt`
- Delete: `android/app/src/main/java/co/terminal/radio/StationAdapter.kt`
- Delete: `android/app/src/main/res/layout/activity_main.xml`
- Delete: `android/app/src/main/res/layout/item_station.xml`

- [ ] Convert `MainActivity` to `ComponentActivity` with `setContent`.
- [ ] Request `POST_NOTIFICATIONS` on Android 13+.
- [ ] Offer `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` when not ignoring optimizations.
- [ ] Auto-call `viewModel.startAutoPlay()` on first composition.
- [ ] Build Material 3 UI showing playback state, current URL, network status, elapsed duration, and play/pause/stop/reconnect buttons.
- [ ] Do not stop playback from Activity lifecycle callbacks.

### Task 8: Update Manifest And Android Auto Metadata

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/automotive_app_desc.xml`

- [ ] Add required playback, network, wake lock, and notification permissions.
- [ ] Register `RadioPlaybackService` with `foregroundServiceType="mediaPlayback"` and Media3 session intent action.
- [ ] Add Android Auto media app metadata pointing to `@xml/automotive_app_desc`.
- [ ] Remove the old `RadioReceiver` declaration.

### Task 9: Remove Unused Legacy Files And Imports

**Files:**
- Delete: `android/app/src/main/java/co/terminal/radio/RadioReceiver.kt`
- Inspect: `android/app/src/main/res/drawable/*.xml`
- Inspect: `android/app/src/main/res/values/*.xml`

- [ ] Remove stale receiver and adapter files.
- [ ] Keep existing icons if referenced by notification/app icon.
- [ ] Keep themes/colors only if compatible with Compose host activity.

### Task 10: Verify Build Through Makefile

**Files:**
- Inspect: `Makefile`

- [ ] Run `make build` from repository root.
- [ ] If local SDK/Gradle setup fails due environment, run or report `make docker` as fallback.
- [ ] Fix compile errors caused by API mismatch or unused generated binding references.
- [ ] Confirm the APK is generated at `TerminalRadio-v1.2.0.apk` through the existing Makefile copy step.

## Self-Review

- Spec coverage: Compose, MVVM, Media3 ExoPlayer, foreground service, MediaSession, notification controls, auto-play, M3U default station, error reconnect, network recovery, audio focus, noisy handling, watchdog, permissions, battery prompt, Android Auto, Makefile build, and version bump are covered.
- Placeholder scan: no placeholder tasks remain.
- Type consistency: shared models are introduced before parser, playback manager, service, repository, ViewModel, and UI tasks use them.
