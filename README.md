# AURIX — ONE APP. ALL YOUR MUSIC.

AURIX (`v1.2.6`) is a modern Android music player and sound finder app built with Kotlin, Jetpack Compose, and Material 3.

## Features

- **Streaming & Local Music**: Stream tracks via the Audius public API (`/v1/tracks/trending`, `/v1/tracks/search`) and import local audio files from device storage.
- **YouTube Link & Sound Finder**: Analyze links, filter by Khmer/international moods and categories, and play tracks seamlessly.
- **Synchronized Lyrics**: Real-time synced `.lrc` lyrics auto-scrolling with playback via the LRCLIB API, with plain lyrics fallback.
- **Background Playback**: Powered by AndroidX Media3 (ExoPlayer) with media controls.
- **GitHub OTA Updates**: Built-in release checker (`MEYYYNASH/AURIX`) with release notes display, APK download, and in-app installer.
- **Customization & Localization**: English and Khmer (`values-km`), System/Light/Dark themes, 7 accent colors, dynamic artwork background, and reduced motion support.

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
