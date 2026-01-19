# URL Radio Droid

A minimalist Android application for listening to internet radio via direct streaming links (HTTP/HTTPS).

## Features

- Add radio stations with name and stream URL
- View list of saved stations
- Play streams using ExoPlayer
- Background playback support
- Local data storage using Room Database
- Modern liquid glass UI design
- Media notifications with playback controls
- Network availability check before playback
- URL validation when adding stations

## Requirements

- Android 10 (API 29) or higher
- Android Studio or compatible IDE
- Gradle 9.0+
- Java 24 (for compilation)

## Building

1. Clone the repository or open the project in Android Studio
2. Sync Gradle dependencies
3. Build the project: `./gradlew build`
4. Install on device: `./gradlew installDebug`

## Usage

1. Launch the application
2. Tap the "Add" button (FAB)
3. Enter station name and stream URL (e.g., `http://stream.example.com:8000/stream`)
4. Tap "Save"
5. Select a station from the list to play
6. Use Play/Stop button to control playback
7. Playback continues in background with media notification

## Technologies

- **Kotlin** - primary development language
- **AndroidX** - support libraries (AppCompat, Material Components)
- **Room Database** - local database for storing stations
- **Media3 (ExoPlayer)** - audio stream playback
- **ViewBinding** - view binding
- **Coroutines** - asynchronous programming
- **KSP** - Room annotation processing
- **MediaSession** - integration with Android media playback system

## Project Structure

```
app/src/main/
├── java/com/urlradiodroid/
│   ├── data/
│   │   ├── RadioStation.kt          # Entity for radio station
│   │   ├── RadioStationDao.kt        # DAO for database operations
│   │   └── AppDatabase.kt            # Room database
│   └── ui/
│       ├── MainActivity.kt           # Main screen with station list
│       ├── AddStationActivity.kt     # Add station screen
│       ├── PlaybackActivity.kt        # Playback control screen
│       ├── RadioPlaybackService.kt    # Background playback service
│       └── StationAdapter.kt         # RecyclerView adapter
└── res/
    ├── layout/                       # XML layouts
    ├── values/                       # Resources (strings, colors, themes)
    └── drawable/                     # Graphics resources
```

## Dependencies

Main project dependencies:

- `androidx.core:core-ktx:1.17.0`
- `androidx.appcompat:appcompat:1.7.1`
- `com.google.android.material:material:1.13.0`
- `androidx.room:room-runtime:2.8.4` + `room-ktx:2.8.4`
- `androidx.media3:media3-exoplayer:1.9.0`
- `androidx.media3:media3-ui:1.9.0`
- `androidx.media3:media3-session:1.9.0`

## Testing

The project includes unit tests for main components:

- Tests for `RadioStation` data model
- Tests for DAO using in-memory database
- URL validation tests
- Tests for station list adapter

### Running Tests Locally

Run all unit tests:
```bash
./gradlew test
```

Run tests for a specific module:
```bash
./gradlew :app:test
```

View test results:
```bash
./gradlew test --info
```

Test results are available in `app/build/test-results/` directory.

## Code Quality

### Kotlin Linting (ktlint)

The project uses [ktlint](https://ktlint.github.io/) for code style checking and formatting.

Check code style:
```bash
./gradlew ktlintCheck
```

Auto-fix code style issues:
```bash
./gradlew ktlintFormat
```

## CI/CD

GitHub Actions workflow automatically runs on every push and pull request:

- **Tests**: Runs all unit tests
- **Lint**: Checks Kotlin code style with ktlint
- **Build**: Builds the debug APK

View workflow status in the "Actions" tab of your GitHub repository.

## Permissions

The application requests the following permissions:

- `INTERNET` - for stream playback
- `ACCESS_NETWORK_STATE` - for network availability check
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - for background playback
- `POST_NOTIFICATIONS` - for media notifications (Android 13+)

## License

Project created for personal use.
