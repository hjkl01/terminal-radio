# Radio Player Dashboard Redesign Design

## Goal

Redesign the existing Compose screen into a player-style dashboard while keeping current playback behavior unchanged.

## Approved Direction

Use a player dashboard layout:

- A large now-playing card at the top.
- Animated status artwork that changes by playback state.
- Current station name as the primary title and URL as secondary metadata.
- Compact status chips for playback state, network, elapsed time, and source.
- Large central playback controls.
- A redesigned playlist with station names, URLs, selected state, and compact action affordance.

## Scope

In scope:

- Refactor `RadioScreen.kt` into smaller Compose components.
- Add simple Compose animations for playing, buffering, paused, stopped, and error states.
- Keep import, restore, previous, next, play, pause, stop, reconnect, and select-station behavior.
- Bump app version in `android/app/build.gradle.kts`.
- Verify with `make docker`.

Out of scope:

- Playback service changes.
- MediaSession behavior changes.
- New station data fields.
- Theme system extraction.
