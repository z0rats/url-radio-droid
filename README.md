# 📻 Freqcast — Internet Radio Player

[![codecov](https://codecov.io/gh/z0rats/freqcast/branch/master/graph/badge.svg)](https://codecov.io/gh/z0rats/freqcast)

A minimalist Android app for listening to internet radio streams (HTTP/HTTPS, incl. HLS `.m3u8`), built with Kotlin, Jetpack Compose, and Media3/ExoPlayer.

<p align="center">
  <img src="docs/screenshots/1.jpg" alt="App main screen" width="220" />
  <img src="docs/screenshots/2.jpg" alt="App add station screen" width="220" />
  <img src="docs/screenshots/3.jpg" alt="App playback screen" width="220" />
</p>

## ✨ Features

- 🔎 **Discover stations** — search the [Radio Browser](https://api.radio-browser.info/) directory by name, genre, country, language, or "near me" 📍, and add results straight to your library (with icon, genre, and HLS flag auto-filled)
- ➕ **Manual add** — enter a name and stream URL, validated with a live reachability check before saving
- 📜 Station list with search, drag-to-reorder ✋, swipe to edit/share/delete (with undo ↩️), and custom emoji/image icons 🖼️
- 🔁 **Background playback** via a media notification, with auto-resume after the system kills the app and capped-retry reconnect on network loss
- ⏪ **Timeshift** — rewind 5s or jump to live for single-URL streams (not for HLS)
- 🎶 Live track title from ICY metadata, with one-tap copy-to-clipboard 📋
- 😴 **Sleep timer** and ⏰ **wake-up alarms** (multiple daily alarms, each with its own time + station)
- 🏠 **Home screen widget** and 🚗 **Android Auto** support
- 📤 **Export / import** your station list as JSON, or import an OPML/M3U/PLS playlist
- 📱 Two-pane layout on tablets/foldables, landscape-optimized playback screen
- 🌐 Localized: English, Russian, Spanish, Simplified Chinese

## 📲 Install on your phone

Grab the latest APK from the [Releases](../../releases) page and install it — no Play Store listing. 🎉

Requires **Android 10 (API 29)** or higher.

## 🔨 Set up for development

1. 🧬 Clone the repo and open it in Android Studio (or use `./gradlew` directly — the wrapper is committed)
2. ☕ Requires **JDK 24** and **Gradle 9.0+** (bundled via the wrapper)
3. 🏗️ Build: `./gradlew build`, or `./gradlew installDebug` to install on a connected device/emulator
4. 🧰 `make` wraps common tasks: `make format`, `make lint`, `make test`, `make build`, `make check`, `make run`

### 🧪 Testing & coverage

```bash
./gradlew test                  # unit tests
./gradlew verifyRoborazziDebug  # screenshot (pixel-diff) tests — also run in CI
./gradlew koverHtmlReportDebug  # local coverage report -> app/build/reports/kover/htmlDebug/index.html
```

📊 Coverage is uploaded to [Codecov](https://codecov.io/gh/z0rats/freqcast) on every push/PR (informational, not a merge gate). CI (`.github/workflows/ci.yml`) also runs `ktlintCheck` ✅ and builds the debug APK 🏗️ on every push/PR; `release.yml` builds and signs 🔏 a release APK on version tags.

## 🔐 Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | 🌐 stream playback, connectivity checks |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 🔁 background playback |
| `POST_NOTIFICATIONS` | 🔔 media notification (Android 13+) |
| `WAKE_LOCK` | 🔋 keep the network alive while playing with the screen off |
| `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` | ⏰ wake-up alarms survive reboot and fire at the exact time |
| `ACCESS_COARSE_LOCATION` | 📍 only used for Discover's "near me" search; sent for that one search, never stored |

## 💖 Support

If you'd like to support development, see [FUNDING.md](FUNDING.md) (Boosty / crypto).

## 📄 License

Licensed under [PolyForm Noncommercial 1.0.0](LICENSE) — free to view, fork, and modify for any noncommercial purpose. Commercial use requires the licensor's permission; contact via [FUNDING.md](FUNDING.md) or open an issue.
