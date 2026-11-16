# AURIX — ONE APP. ALL YOUR MUSIC.

AURIX (`v1.6.6`, release date `2026-11-16`) is a modern Android music player and sound finder app built with Kotlin, Jetpack Compose, and Material 3.

## Features

- **Because You Listened**: 30-day vocal sad & chill recommendation engine (`"Sad Vibes for You"` and `"Chill Mix for You"`, 20 songs each) with `Match %` badges, `"Because you listened to X"` reasons, 👍/👎 feedback, artist diversity, and cold-start support.
- **MoodFlow — Intelligent Mood Shuffle**: 5-dimensional mood-aware shuffle (`happy`, `sad`, `calm`, `energetic`, `romantic`) blending the active and recent tracks with 60/25/10/5 roulette buckets and live `Match %` display in Now Playing.
- **YouTube Search, Format Picker & Local Music**: Search full songs on YouTube, preview or pick audio format & quality (M4A / WebM), explore 30-second iTunes previews, and import local audio files from device storage.
- **Link Matcher & Synced Lyrics**: Match YouTube links in Search and follow real-time synced `.lrc` lyrics via LRCLIB, with plain lyrics fallback.
- **Background Playback**: Powered by AndroidX Media3 (ExoPlayer) with equalizer, crossfade, and media controls.
- **Animated OTA Updates (`UpdateManager`)**: `version.json`-backed updater (`MEYYYNASH/AURIX`) with resumable APK downloads, real-time speed & progress, SHA-256 and package signature verification, reduced-motion aware planet animations, and in-app installer.
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
