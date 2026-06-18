# Terminal Radio Compose Playback Design

## Background

`tasks.md` requires a minimal Android radio app built with Kotlin, Jetpack Compose, AndroidX Media3 ExoPlayer, foreground playback, MediaSession, notification controls, automatic reconnect, network recovery, audio-focus recovery, watchdog recovery, and Android Auto compatibility.

The current project is a single Android module under `android/app`, but it still uses AppCompat, ViewBinding, RecyclerView XML layouts, and stores playback directly in `RadioApplication`. That structure does not satisfy the Compose + MVVM requirement and makes long-running background playback fragile.

## Decision

Use a single-module rewrite inside the existing `android/app` module. Keep the Gradle project shape and assets, but replace the activity/application-centered playback model with a Compose UI, a ViewModel-facing state model, and a Media3 `MediaSessionService`-based playback layer.

This keeps the change focused while satisfying the requested architecture and Android background playback constraints.

## Architecture

### UI Layer

`MainActivity` will become a Compose entry point. It will:

- Request Android 13+ notification permission when needed.
- Offer an intent-based battery optimization whitelist prompt.
- Start and bind to the playback service through explicit actions.
- Render a Material 3 screen showing current playback state, current URL, network state, elapsed playback duration, and buttons for play, pause, stop, and reconnect.

`RadioViewModel` will expose immutable UI state and user actions. It will not own ExoPlayer directly. It will observe playback state published by the service/client layer and convert it into Compose-friendly state.

### Playback Layer

`RadioPlaybackService` will extend Media3 `MediaSessionService`. It will own:

- `ExoPlayer`
- `MediaSession`
- Foreground media notification integration
- Wake mode and audio attributes
- Service lifecycle cleanup

The service is the long-running playback owner. Closing the UI must not stop playback unless the user presses Stop.

`RadioPlayerManager` will wrap player behavior and recovery policy:

- Load `assets/cnr.m3u` and choose the station named `音乐之声` by default.
- Set the selected station URL as a Media3 `MediaItem` and start playback automatically.
- Listen to `Player.Listener.onPlayerError` and retry after 3 seconds.
- Track whether the user explicitly paused or stopped playback, so automatic recovery does not fight intentional user controls.
- Run a 30-second watchdog that calls `prepare()` and `play()` when playback unexpectedly stops.
- Handle Audio Focus transient loss by pausing and focus gain by resuming.
- Handle permanent focus loss by recording state and checking every 10 seconds; if playback remains stopped without user pause, request focus and resume.
- Handle audio becoming noisy by pausing as Android audio apps are expected to do.

### Network Layer

`NetworkMonitor` will use `ConnectivityManager.NetworkCallback` instead of deprecated broadcast network APIs. It will publish online/offline state and notify the playback manager when network becomes available. When network returns and the user has not manually stopped playback, playback will be prepared and started again.

### Data Layer

`M3uParser` can remain small and pure. It will parse `cnr.m3u` into `Station(name, url)`. The app will prefer the exact station name `音乐之声`; if missing, it will fall back to the first station to avoid launch failure.

## Android Manifest And Permissions

The manifest will declare:

- `android.permission.INTERNET`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.WAKE_LOCK`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.POST_NOTIFICATIONS`

It will register `RadioPlaybackService` with `android:foregroundServiceType="mediaPlayback"`, the Media3 session service action, and exported settings appropriate for media browser clients such as Android Auto.

## Gradle And Versioning

Update `android/app/build.gradle.kts` to enable Compose, add Compose Material 3, lifecycle ViewModel Compose, activity Compose, and the required Media3 dependencies. Remove ViewBinding/AppCompat/RecyclerView dependencies that become unused.

Per repository rule, every code change also updates `android/app/build.gradle.kts` version metadata. The implementation should bump `versionCode` and `versionName` from the current `6` / `1.1.5`.

## Error Handling

Playback recovery has three levels:

1. Immediate player failure recovery: `onPlayerError` schedules a reconnect after 3 seconds.
2. Network recovery: when connectivity returns, playback restarts if the user did not intentionally stop it.
3. Watchdog recovery: every 30 seconds, unexpected non-playing state triggers `prepare()` and `play()`.

User intent has priority. If the user presses Pause or Stop, recovery loops must not immediately restart playback. Reconnect is the explicit manual override.

## Testing And Verification

Use the Makefile where available:

- `make build` for local Gradle debug APK build.
- If local Android SDK is unavailable, use `make docker`.

Implementation verification should also inspect Gradle warnings and ensure the generated APK remains installable on normal ARM Android phones. Manual runtime checks should cover launch auto-play, background playback, lock-screen notification controls, reconnect button, network recovery, and pause/stop user intent.

## Non-Goals

- Do not split into multiple Gradle modules.
- Do not add account, favorites, search, or complex station management.
- Do not preserve the old XML UI or RecyclerView architecture.
- Do not stop playback from `Activity.onDestroy`; the service owns playback lifetime.
