# Lockscreen Controls, Station List, and Custom M3U Design

## Goal

Terminal Radio should behave like a normal Android media player: lock-screen media controls, visible station list from `cnr.m3u`, local custom M3U import, and safe pause when Bluetooth/audio output disconnects.

## Scope

- Upgrade the foreground notification to a media-style notification tied to the active `MediaSession`.
- Expose all stations parsed from bundled `assets/cnr.m3u` to the Compose UI.
- Let the user import a local `.m3u` file through Android's file picker.
- Persist the imported M3U content in app-private storage and use it as the current station source until the user restores the built-in list.
- Pause playback on noisy audio route changes and Bluetooth disconnects as a user/safety pause so WatchDog does not resume automatically.

## Architecture

Keep the existing single-module MVVM structure. `RadioPlayerManager` remains the playback owner and source of truth for station list, selected station, and player state. `RadioPlaybackService` translates foreground service intents into manager calls and owns the media notification. `RadioControlRepository` exposes app actions to `RadioViewModel`, and `MainActivity` handles the file picker because it owns the Activity Result API.

## UI

The screen keeps the status card and playback buttons, then adds a station list. Each row shows station name and URL, highlights the currently selected station, and clicking a row starts that station. A compact source-control row provides `导入 m3u` and `恢复内置列表` actions.

## Playback Behavior

- Startup still auto-plays `音乐之声` when available.
- Previous/next actions move through the current station list and wrap around.
- Selecting a station clears user pause/stop flags and starts playback immediately.
- If a custom M3U is invalid or empty, state reports an error and keeps the previous usable list.

## Lock-Screen Controls

The service notification uses `androidx.media.app.NotificationCompat.MediaStyle` with the MediaSession token. Compact lock-screen actions expose previous, play/pause, and next. The expanded notification can also include reconnect and stop.

## Bluetooth Disconnect

`ACTION_AUDIO_BECOMING_NOISY` and Bluetooth disconnect broadcasts call a dedicated safety pause path. This sets the pause flag just like a user pause, preventing automatic WatchDog/network/focus recovery from restarting audio after the output disconnects.

## Verification

- Build with `make docker`.
- Confirm generated APK name reflects the bumped Android version.
- Manual device verification remains needed for actual lock-screen rendering and Bluetooth disconnect behavior.
