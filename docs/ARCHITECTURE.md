# Architecture

## Overview

Cheshers uses Kotlin, Jetpack Compose/Material 3, MVVM, Hilt, Room, DataStore, Kotlin Flow, Retrofit/OkHttp, Socket.IO, and WorkManager. Feature screens observe immutable UI state from ViewModels; repositories own persistence and transport. The chess rules wrapper is independent of Android UI.

```text
Compose screens
      │ state / events
      ▼
ViewModels ───────────────► OpenRouter text AI
      │                              │
      ▼                              ▼
Repositories ◄── local validation ── chesslib
      │
      ├────────► Room / DataStore / encrypted preferences
      └────────► Self-hosted Socket.IO + REST backend
```

## Rules and game state

`core-chess` wraps chesslib rather than reimplementing move legality. `ChessGame` owns one thread-confined game and exposes value snapshots for Compose. All external moves—touch input, AI responses, imported replay steps, and server updates—must match a locally generated legal move before application.

The wrapper handles castling, en passant, promotion, check/checkmate, stalemate, insufficient material, fifty-move and repetition status, SAN generation, PGN headers/move text, and undo. Original FEN plus UCI history is sufficient to reconstruct a local game. Active saves and completion updates are serialized to avoid overwriting a finished game with a late active-state save.

Clocks use elapsed-time deltas and server timestamps rather than network polling. The online server remains authoritative for turns, results, draw offers, resignation, flags, ratings, and reconnection state.

## AI pipeline

`core-ai` sends chat-completions requests to OpenRouter. Each generated move is validated against the current chesslib position. Invalid or failed move proposals are retried with bounded exponential backoff; requests have 20-second timeouts. Position advice and analysis are cached in Room using position/model-aware keys. A request mutex applies client-side throttling and cancellable coroutines prevent stale UI updates.

Only offline bot play uses random legal fallback. Hints and analysis never invent a move when AI is unavailable; they expose an error/loading state instead. See [OPENROUTER.md](OPENROUTER.md).

## Persistence

- **Room:** profiles, active/completed games, AI cache, quests, achievements, cosmetics, and progression records.
- **DataStore:** non-secret preferences including themes, accessibility, model ID, and backend URL.
- **EncryptedSharedPreferences:** OpenRouter key and online JWT/session metadata. If Android Keystore initialization fails, secrets remain in memory only and Settings displays a warning.
- **FileProvider:** grants temporary read access to user-created `.pgn` files for Android's share sheet.

There is no background polling. A unique periodic WorkManager task refreshes quests near UTC midnight and optionally posts a notification. Socket.IO connections are lifecycle/UI driven.

## Online boundary

The app supports guest and registered sessions. Password registration/login is REST; JWT authentication is also required for Socket.IO. Tokens are stored encrypted on Android. The Node service uses bcrypt for registered passwords, zod input validation, security headers, request limits, chat sanitization/rate limiting, SQLite transactions, and reconnectable persisted rooms.

The Android client does not apply a second local Elo adjustment after an online result. It records the server-confirmed result/history and synchronized rating. Completed games can be opened in the same analysis/replay UI as offline games.

A production deployment must terminate TLS and forward WebSocket upgrades. The included service is source and a test harness, not a deployed public service.

## Progression

Transactional progression updates keep game results, XP, coins, achievements, quests, and unlock state consistent. Quests include daily and weekly schedules; a deterministic daily puzzle tracks streaks. Cosmetics affect board palettes or Unicode piece presentation, not gameplay strength. No currency is sold and no content is premium-locked.

## UI and accessibility

Compose state drives board rendering, clocks, dialogs, navigation, and settings. The board supports drag and tap. Each square exposes coordinate, piece, selection/legal-target/check state, and a semantic click action for assistive technology. Other options include legal dots, coordinates, colorblind-friendly colors/patterns, animation speed, move confirmation, haptics, sounds, and system/light/dark themes.

## Size and build

`minSdk` is 24 and `targetSdk`/`compileSdk` are 36. Release builds enable R8 and resource shrinking. The project deliberately avoids bundled engines, large media, and the extended Material icon artifact. Original WAV effects and vector resources keep the binary small.
