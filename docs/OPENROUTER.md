# OpenRouter configuration and behavior

Cheshers uses OpenRouter's **chat completions** API for bot move proposals, hints, evaluation/coach analysis, and post-game blunder commentary. It does not call Stockfish, Leela, or another chess engine.

## Add a key

### Runtime (recommended for a distributed APK)

Open **Settings → OpenRouter AI**, paste a restricted key, select or enter a model ID, and save. The key is stored using `EncryptedSharedPreferences`; the app warns if secure storage cannot initialize and keeps the value only in memory in that case.

### Private local build

Copy `local.properties.example` to the gitignored `local.properties`:

```properties
OPENROUTER_API=your_private_key
```

The app Gradle script reads this value—or the `OPENROUTER_API` environment variable—and defines `BuildConfig.OPENROUTER_API`. The source repository contains no key. Do not print the value in build logs, commit it, or publish a credential-bearing APK.

**APKs are not secret stores.** Anything compiled into a client can be extracted. Use OpenRouter restrictions, spending limits, and key rotation. Runtime entry is safer for public builds.

## Models

Settings includes several chat-model shortcuts, a custom model-ID field, and a **Refresh models** action backed by OpenRouter's `/models` endpoint. The selected model must support chat completions and JSON-object response formatting. Model availability and pricing are controlled by OpenRouter and can change; the app does not sell or include model access.

Requests use `Authorization: Bearer …`, application identification headers, and a JSON response format. No HTTP logging interceptor is installed, so bearer tokens and prompts are not logged by the app transport.

## Validation and failure handling

1. Build a position/task-specific chat prompt.
2. Send a request with a 20-second network timeout.
3. Parse the response and validate any proposed UCI move against chesslib's current legal-move list.
4. Retry failed/invalid attempts up to three total attempts with bounded backoff.
5. Cache valid position advice/analysis in Room to avoid repeated requests.
6. For **offline bots only**, select a random legal move if AI cannot provide one. Hints and analysis report unavailability rather than fabricating content.

Coroutines are cancellable, bot turns ignore stale responses, and request throttling avoids bursts. AI move quality is not guaranteed even at higher difficulty labels; difficulty and personality shape prompts and fallback behavior, not engine-strength claims.

## Data sent

Depending on the requested feature, prompts can include FEN, legal moves, PGN/move history, game status, difficulty/personality, and requested coaching context. This data is sent to OpenRouter and the selected model provider under their privacy terms. No advertising/analytics identifier is attached by Cheshers.

OpenRouter charges, free-model limits, and network access are external to the app. Offline pass-and-play, puzzles, local replay, and random-legal bot fallback remain usable without a key.

## Troubleshooting

- **No key:** add one in Settings or use random-legal offline fallback.
- **Model rejected:** choose a model that supports JSON response format, or enter another model ID.
- **Timeout/rate limit:** wait and retry; the app already limits attempts and request frequency.
- **Invalid move:** it is never applied; the retry/fallback path handles it.
- **Unexpected charges:** revoke/rotate the key in OpenRouter and review provider-side limits and usage.
