# Cheshers

**Chess, free forever.** Cheshers is an open-source Android chess app with no ads, subscriptions, in-app purchases, premium locks, analytics trackers, or bundled chess engine.

- **Offline:** pass-and-play and OpenRouter-powered bots, six difficulty levels, three personalities, side selection, clocks, hints, undo, draw and resign.
- **Online:** rated matchmaking, friend invite codes, synchronized clocks, reconnect/resume, chat, mute/report, draw/resign, and review. Requires your own backend; no public server is deployed by this repository.
- **Improve:** 12 bundled legal puzzles, a deterministic daily puzzle and streaks, free analysis, AI evaluation/coach commentary, history, replay, and `.pgn` export.
- **Progress:** daily/weekly quests, achievements, XP/levels, coins, unlockable board themes and piece sets.
- **Profiles:** editable local profile, per-time-control ratings, stats, global/friends/weekly backend leaderboards.
- **Personalize:** Material 3, light/dark/system mode, responsive Compose layouts, drag or tap moves, legal targets, last-move/check highlights, accessible square actions, original sounds, haptics, and onboarding.

Cheshers uses [chesslib](https://github.com/bhlangonijr/chesslib) for local chess rules and [OpenRouter chat completions](docs/OPENROUTER.md) for AI prose and move proposals. **Stockfish, Leela, and other engines are not included or used.** If an offline bot cannot get a valid AI move, it falls back to a random legal move so the game can continue.

## Requirements

- Android 7.0 (API 24) or newer to install.
- Android Studio with JDK 17 and Android SDK 36 to build.
- Node.js 20 or newer supported LTS for the optional backend (`better-sqlite3` is pinned for Node 20 compatibility).
- An OpenRouter account/key for bot proposals, hints, evaluation, and coach analysis. OpenRouter usage charges, if any, are separate from the free app.

## Build in Android Studio

1. Clone the repository and open its root in Android Studio.
2. Let Gradle sync. The wrapper uses Gradle 8.13; the project uses JDK 17 and SDK 36.
3. Copy `local.properties.example` to `local.properties` and set `sdk.dir`. The file is gitignored.
4. Optionally configure a private OpenRouter key and backend as shown below.
5. Run the `app` configuration, or build from a terminal:

```bash
./gradlew :app:assembleDebug
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

### Local, private configuration

```properties
sdk.dir=/absolute/path/to/Android/Sdk
OPENROUTER_API=
CHESHERS_BACKEND_URL=https://your-backend.example.com
```

`OPENROUTER_API` is injected into `BuildConfig.OPENROUTER_API` only when supplied locally or through the same-named environment variable. It is never hardcoded in source and must never be committed or logged. You can instead leave it blank and enter a key at runtime in **Settings → OpenRouter AI**, where it is stored with `EncryptedSharedPreferences`.

> Any secret compiled into a client APK can be extracted. Do not distribute a build containing your private key. Use a restricted, revocable key and set provider-side spending limits. Public/release builds should normally leave `OPENROUTER_API` empty.

The debug backend defaults to `http://10.0.2.2:3000` for the Android emulator. Release builds allow HTTPS/WSS only. Configure a deployed backend URL in **Settings → Self-hosted online backend**.

## Signed release

Keep signing material outside the repository. Add the following only to an untracked `local.properties` or use environment variables:

```properties
CHESHERS_STORE_FILE=/absolute/path/to/private-cheshers.jks
CHESHERS_STORE_PASSWORD=your-private-store-password
CHESHERS_KEY_ALIAS=cheshers
CHESHERS_KEY_PASSWORD=your-private-key-password
```

Then run:

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Release builds use R8 and resource shrinking. Back up the keystore and passwords securely; losing them prevents signing future updates with the same identity. Never publish the keystore or credentials.

## Tests

```bash
# JVM and Android unit tests
./gradlew :core-chess:test :core-ai:test \
  :core-data:testDebugUnitTest :feature-game:testDebugUnitTest \
  :feature-quests:testDebugUnitTest :feature-profile:testDebugUnitTest \
  :feature-online:testDebugUnitTest :app:testDebugUnitTest

# Compile the five instrumented Compose UI tests
./gradlew :app:compileDebugAndroidTestKotlin

# Execute them on a running emulator or device
./gradlew :app:connectedDebugAndroidTest

# Backend unit and real two-client integration tests
cd backend
npm ci
npm test
```

The UI suite covers onboarding, semantic tap-to-move, move confirmation, and promotion choice. Puzzle tests verify that every bundled solution is legal and each advertised mate-in-one actually mates.

## Backend

The `backend/` directory contains a self-hosted Node.js, Express, Socket.IO, and SQLite service. It includes guests and email/password accounts, authoritative move validation, matchmaking, invites, resumable rooms, server clocks, chat/report/friends, Elo, history, and leaderboards.

**No hosted instance is implied or deployed.** See [docs/BACKEND.md](docs/BACKEND.md) for setup, HTTPS/WSS reverse-proxy configuration, environment variables, and testing.

## Project map

| Module | Responsibility |
| --- | --- |
| `:app` | Application shell, navigation, onboarding, home, settings, theme, sharing, WorkManager setup |
| `:core-chess` | chesslib rules, snapshots, SAN/PGN, clocks, status, undo, ratings |
| `:core-ai` | OpenRouter transport, prompts, validation, retries, throttling, caching models, random-legal bot fallback |
| `:core-data` | Room, DataStore, encrypted settings/sessions, active games, history, progression, DI |
| `:feature-game` | Offline game/setup, accessible board, puzzles, analysis, original sounds |
| `:feature-online` | Online authentication/lobby/game UI and Socket.IO synchronization |
| `:feature-quests` | Quest/achievement/shop UI and daily reset worker |
| `:feature-profile` | Profile, stats, history/replay, PGN sharing, leaderboards |
| `backend/` | Self-hosted authoritative multiplayer service and integration harness |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for data flow and design details.

## Privacy and security

Offline games, progression, settings, and AI cache stay on the device. OpenRouter receives the prompts/positions needed for requested AI features. Your backend operator receives online account, game, chat, and rating data. There are no third-party analytics or advertising SDKs. Credentials are excluded from backups and are never written to HTTP logs.

See [PRIVACY.md](PRIVACY.md), [docs/OPENROUTER.md](docs/OPENROUTER.md), and [docs/BACKEND.md](docs/BACKEND.md).

## Open source and assets

Source and backend are **AGPL-3.0-or-later**; see [LICENSE](LICENSE). Original launcher artwork, procedural sound effects, and other original visual/audio assets are dedicated to **CC0-1.0**; see [docs/ASSET_LICENSES.md](docs/ASSET_LICENSES.md). Third-party attributions are in [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

The app intentionally uses original branding and UI. It does not copy Chess.com or bundle copyrighted piece-set artwork.
