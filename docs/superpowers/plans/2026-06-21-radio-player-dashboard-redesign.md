# Radio Player Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current functional Compose page into a polished player dashboard.

**Architecture:** Keep playback state and service APIs unchanged. Refactor `RadioScreen.kt` into focused stateless composables driven by `PlaybackUiState`, and bump the Android app version.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL.

---

### Task 1: Version Bump

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] Change `versionCode` from `13` to `14`.
- [ ] Change `versionName` from `1.2.6` to `1.2.7`.

### Task 2: Compose Dashboard Redesign

**Files:**
- Modify: `android/app/src/main/java/co/terminal/radio/RadioScreen.kt`

- [ ] Replace the plain list-first layout with a dashboard layout.
- [ ] Add `NowPlayingCard`, `PlaybackVisualizer`, `StatusChip`, `ControlPanel`, `LibraryActions`, `PlaylistSection`, and redesigned `StationRow` composables.
- [ ] Display station name and URL together in the now-playing card and selected playlist row.
- [ ] Drive animation from `PlaybackStatus` only; do not change playback logic.
- [ ] Keep all existing callback parameters and behavior.

### Task 3: Verification

**Command:**
- Run: `make docker`
- Expected: Gradle build succeeds and copies `TerminalRadio-v1.2.7.apk`.

## Self-Review

- Covers dynamic status icon requirement.
- Covers current station name plus URL requirement.
- Keeps feature behavior unchanged.
- Includes required version bump.
- Uses Makefile-based verification.
