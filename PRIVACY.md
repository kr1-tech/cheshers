# Cheshers privacy overview

Cheshers has no advertising, analytics trackers, subscriptions, in-app purchases, or third-party usage profiling.

## On-device data

Offline games, active-game state, history, progression, quests, achievements, cosmetics, puzzle streaks, and AI cache are stored locally in Room. Preferences use DataStore. OpenRouter keys and online session tokens use `EncryptedSharedPreferences`; if secure storage fails, secrets remain in memory only and Settings warns the user. Encrypted preferences are excluded from Android backup. PGN files are created only when the user requests export/share and are shared through a non-exported `FileProvider`.

## OpenRouter

When the user requests a bot move, hint, evaluation, or coach review, relevant chess positions/history and task prompts are sent to OpenRouter and the selected model provider. A bearer key is sent to authenticate the request. The app installs no HTTP body/header logging interceptor. Provider availability, retention, and charges are governed by OpenRouter/model-provider terms.

No AI request is required for pass-and-play, bundled puzzles, local replay, or random-legal bot fallback.

## Self-hosted online service

If the user configures a backend, that operator processes the chosen username and, for registered accounts, email and a bcrypt password hash. The service stores JWT-identified accounts, ratings, game history, active games, friend relationships, chat messages, and reports. Chat is sanitized and rate-limited; transport must use HTTPS/WSS in release builds.

No public Cheshers backend is deployed by this source repository. The operator of the server you choose is responsible for its privacy notice, security, retention, account deletion, and legal compliance. A guest token that is lost may not be recoverable.

## Notifications and background work

A periodic local WorkManager task refreshes quests and can show a notification when enabled. It does not poll a chess server. Notification permission is requested only on Android versions that require it.

## User controls

Users can edit their local profile, change/clear the OpenRouter key in Settings, sign out of the online service, choose whether to use AI or online features, disable notifications, and export their own game PGNs. Uninstalling removes app-local Android data under normal Android behavior; it does not delete data already sent to OpenRouter or a backend.
