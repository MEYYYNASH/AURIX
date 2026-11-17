# AURIX — ONE APP. ALL YOUR MUSIC.

AURIX (`v1.8.0`, release date `2026-11-17`) is a modern Android music player and sound finder app built with Kotlin, Jetpack Compose, and Material 3.

## Features

- **Home Discovery**: Featured Hero Spotlight banner, **New Albums** grid, **Popular YouTube Song List** with format & quality downloads, and **Today's Hits** & Daily Top charts powered by iTunes & Apple Charts.
- **Liquid Glass Player & Up Next Queue**: Ambient artwork aura in Now Playing, swipe-to-skip MiniPlayer, interactive **Up Next** queue drawer (reorder, jump, Play Next, Add to Queue), and Sleep Timer with gentle fade-out.
- **Audio Studio (EQ + 3D Surround)**: 5-band Bezier spectrum equalizer with `Bass Boost`, hardware `3D Surround Virtualizer`, `Lo-Fi` and `Acoustic` presets, playback speed studio, and equal-power crossfade.
- **Because You Listened & MoodFlow**: 30-day vocal sad & chill recommendations plus 5-dimensional mood-aware shuffle with live `Match %` badges.
- **YouTube Search, iTunes 30s & Local Music**: Spring-animated Liquid Glass Search for full songs on YouTube, preview iTunes 30s tracks, pick audio format & quality (M4A / WebM), and import local audio files from device storage.
- **Link Matcher & Synced Lyrics**: Match YouTube links in Search and follow real-time synced `.lrc` lyrics via LRCLIB, with plain lyrics fallback.
- **Background Playback & Downloads**: Powered by AndroidX Media3 (ExoPlayer) plus a `dataSync` foreground `BackgroundDownloadService` for uninterrupted track and update downloads.
- **Animated OTA Updates (`UpdateManager` + `WorkManager`)**: `version.json`-backed updater (`MEYYYNASH/AURIX`) with periodic release notifications, resumable APK downloads, real-time speed & progress, SHA-256 and package signature verification, reduced-motion aware planet animations, and in-app installer.
- **Customization & Localization**: English and Khmer (`values-km`), System/Light/Dark/AMOLED themes, 7 accent colors, dynamic artwork background, and reduced motion support.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose & Material 3
- **Architecture**: MVVM (`AurixViewModel` + `StateFlow`)
- **Playback**: AndroidX Media3 (ExoPlayer)
- **Networking**: Retrofit, Moshi, OkHttp
- **Persistence**: Room Database (Favorites & History) & DataStore Preferences

## Build Signed Release APK & AAB

1. **Generate a Keystore**:
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. **Configure Environment Variables**:
   Set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in your environment or CI/CD secrets.
3. **Build Release APK / AAB via Gradle**:
   ```bash
   gradle :app:assembleRelease
   gradle :app:bundleRelease
   ```
   The generated outputs will be located in `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/`.
