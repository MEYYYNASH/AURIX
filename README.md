# AURIX — ONE APP. ALL YOUR MUSIC.

AURIX is a premium, beautifully crafted Android music player app built with Kotlin, Jetpack Compose, and Material 3.

## ✨ Features

- **Streaming & Local Music**: Stream millions of tracks via the Audius public API (`/v1/tracks/trending`, `/v1/tracks/search`) and import local audio files from your device storage.
- **Synchronized Lyrics**: Real-time synced `.lrc` lyrics auto-scrolling with the music via the LRCLIB API, with plain lyrics fallback.
- **Robust Playback**: Powered by AndroidX Media3 (ExoPlayer) with background audio support and media notifications.
- **Rich Screens & Navigation**:
  - **Splash & Onboarding**: Immersive 4-page onboarding flow on first launch.
  - **Home**: Time-based greeting, Speed Dial (Liked Songs & Trending), Moods filter chips (Chill, Happy, Focus, Workout, Khmer, Party), My songs, Recently played, and Trending on Audius.
  - **Search**: Debounced live search across Audius tracks.
  - **Library**: Segmented tabs for My songs, Favorites, and Recent.
  - **Settings**: Instant language toggle (English / ភាសាខ្មែរ), theme picker (System / Light / Dark), 7 custom accent color themes, dynamic artwork background toggle, and reduced motion settings.
  - **Now Playing Sheet**: Full-screen immersive player with blurred dynamic artwork background, seek bar, play/pause, shuffle, repeat, sleep timer (15, 30, 60 min), and playback speed controls (1x, 1.25x, 1.5x, 0.75x).
  - **Floating Glass Bottom Bar**: 4 main tabs and a 56dp round accent Now Playing button with animated equalizers.

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose & Material 3
- **Architecture**: MVVM with ViewModel and StateFlow
- **Playback**: AndroidX Media3 (ExoPlayer)
- **Networking**: Retrofit & Moshi
- **Persistence**: Room Database (Favorites & Play History) & DataStore Preferences (Settings & Onboarding state)

---

## 📦 How to Build Signed Release APK & AAB

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
