# AURIX — Android APK Build Prompt

Paste this into an AI coding tool (Claude Code, Cursor, Android Studio assistant) to get a real, installable APK that matches the AURIX design.

---

## 1. Role and goal

You are a senior Android engineer and UI designer. Build **AURIX**, a premium music player app for Android, and produce a signed **release APK** (and an AAB for Play Store). Tagline: **ONE APP. ALL YOUR MUSIC.** The look must match the AURIX web design exactly: clean, rounded, spacious, smooth motion. Every button must work. No fake features and no invented integrations.

## 2. Tech stack

- Kotlin, Jetpack Compose, Material 3 (heavily customised to the design below).
- Min SDK 24, target SDK 34 or newer.
- Media3 (ExoPlayer + MediaSession) for playback, with a foreground service, lock-screen and notification controls, and Bluetooth/headset buttons.
- Retrofit + Kotlin serialization for networking, Coil for images.
- Room for favorites and history, DataStore for settings.
- MVVM with a single-activity Compose navigation.
- Gradle Kotlin DSL. Package name `com.aurix.music`.

## 3. Data sources (real, no fake data)

- **Music:** Audius public API. Trending: `GET /v1/tracks/trending`. Search: `GET /v1/tracks/search?query=`. Stream: `/v1/tracks/{id}/stream`. Always send `app_name=aurix`.
- **Lyrics:** LRCLIB `GET /api/get` by title, artist and duration, with a `/api/search` fallback. Parse synced `.lrc` timestamps.
- Show loading, error (with **Try again**) and empty states for every network screen.
- Also let the user add songs from their device with the system file picker (audio files), shown under **My songs**.

## 4. Design tokens

**Light:** background `#EEF2FB`, surface `#FFFFFF`, surface-2 `#F3F6FC`, text `#0F1729`, muted `#6B7488`. **Dark:** background `#12151C`, surface `#1B1F29`, surface-2 `#232836`, text `#FFFFFF`, muted `#8B93A7`.

- Theme: System / Light / Dark (default System).
- Accent default `#3B77F5`. Choices: `#EF4444`, `#EC4899`, `#3B77F5`, `#22C55E`, `#F59E0B`, `#14B8A6`, `#8B5CF6`. The accent drives buttons, progress, selected states and glows.
- Liked heart: `#EF4444`.
- Font: Inter, with Noto Sans Khmer for Khmer text. Headings 28sp bold, section titles 18sp semibold, body 15sp, secondary 13sp muted.
- Corner radii: cards 22dp, small art 12dp, large art 28dp, buttons and chips fully rounded, bottom sheet 32dp top corners.
- No hard borders. Use soft, large, low-opacity shadows.
- Missing cover art: generate a gradient from a hash of the title (two hues 50° apart over a near-black base `#0B1020`).

## 5. Screens

**Splash:** gradient from an accent tint to the background, 96dp rounded logo (music-note icon), wordmark AURIX, tagline in small tracked caps. Fades into onboarding on first launch only.

**Onboarding (4 pages, swipe or arrow):** Your music, all in one place • Beautiful synchronized lyrics • Customize your experience • Ready to listen? Each page has a large gradient hero card with a white icon, a bold headline and a muted description. Skip on the left, dots in the middle (active dot is a stretched pill), round accent arrow on the right, and a full-width **Get started** button on the last page.

**Home:** logo + AURIX header, then greeting (Good morning / afternoon / evening) and "What do you want to listen to?". Then: Speed dial (Liked Songs and Trending cards), Moods chips (Chill, Happy, Focus, Workout, Khmer, Party — each runs a real search), My songs, Recently played (horizontal cards), Trending on Audius (horizontal cards).

**Search:** large rounded field, live results (debounced about 350ms), idle, loading, error and empty states.

**Library:** segmented tabs My songs / Favorites / Recent with helpful empty messages.

**Settings:** Language (English / ភាសាខ្មែរ), Theme, Accent color, Dynamic artwork background toggle, Reduced motion toggle, About (version, credits to Audius and LRCLIB).

## 6. Core components

- **Bottom navigation:** a floating, centered glass bar with four tabs (Home, Search, Library, Settings), each with a line icon and a label. The selected tab gets a soft accent-tinted pill and accent-colored icon. Next to it is a 56dp round accent **Now Playing** button that shows animated equalizer bars while playing. Respect gesture-navigation insets.
- **Song row:** 48dp art, bold title, muted artist, duration, a **⋯ details** button and a heart. The playing row shows animated equalizer bars.
- **Mini player:** floating rounded card above the nav bar with art, title, artist, heart, play/pause and a thin progress line. Tap to open Now Playing. Hidden when nothing has been chosen.
- **Now Playing:** full-screen sheet that slides up, with a blurred, saturated artwork background (can be turned off). Header: close, Player | Lyrics switch, details button. Player pane: large art, title, artist, heart, seek bar with times, previous / big white play-pause / next, then Shuffle, Repeat, Sleep timer (off, 15, 30, 60 min) and Speed (1x, 1.25x, 1.5x, 0.75x) toggles.
- **Lyrics:** synced lines at 25sp bold; the current line is fully opaque with a soft accent glow and auto-scrolls to center, others are faded. Tap a line to seek. Plain lyrics show un-synced.
- **Song details bottom sheet:** art, title, artist; actions Play, Like, Next up, Share (Android share sheet); info rows Duration, Source, Lyrics status, Queue status; **Add to queue**.

## 7. Language

Full English and Khmer (`values` and `values-km` string resources). Switch instantly from Settings and remember the choice. Use proper Khmer wording and make sure Khmer text is never clipped.

## 8. Motion

Easing like `cubic-bezier(.2,.8,.2,1)`. Screen content fades up with a short stagger; Now Playing slides up while the page behind scales to 0.93 and dims; hearts pop on tap; buttons scale down slightly on press. Honor the system animation setting and the in-app Reduced motion toggle.

## 9. Behavior and quality

- Queue with previous (restarts if more than 3 seconds in), next, shuffle, repeat, auto-advance. Playback continues in the background and survives screen rotation.
- Handle audio focus, headphone unplug, and network loss with a short toast (for example "Couldn’t play that song").
- Save favorites, history and settings locally.
- Accessibility: content descriptions on all icon buttons, TalkBack support, minimum 48dp touch targets, good contrast in both themes.
- Required permissions: INTERNET, FOREGROUND\_SERVICE (media playback), POST\_NOTIFICATIONS.

## 10. Deliverables

1. Complete Android Studio project that builds with no errors.
2. App icon (adaptive, accent gradient with music-note) and splash.
3. Step-by-step instructions to generate a signed release APK and AAB, including keystore creation.
4. A short README listing features and known limitations.

---

## Faster alternative: wrap the existing web app

If you already have the AURIX `index.html` and want an APK quickly, use this instead:

> Wrap my existing single-file web app `index.html` into an Android APK using **Capacitor**. Keep the web UI unchanged. Name the app AURIX, package `com.aurix.music`. Add an adaptive icon, splash screen, safe-area handling, and a foreground media service plugin so music keeps playing with the screen off and shows lock-screen controls. Allow network access to `api.audius.co` and `lrclib.net`. Give me the commands to build a signed release APK and explain how to create the keystore.
