# Self-hosted multiplayer backend

The backend is included and tested, but **it is not deployed by this repository**. Online play becomes available only after you run it and configure its URL in the app.

## Features

- Guest sessions and email/username + password registration/login.
- JWT authenticated REST and Socket.IO connections; bcrypt password hashes for registered accounts.
- Rated matchmaking and six-character friend challenges.
- Server-authoritative legal moves, checkmate/draw detection, resignation, draw offers, and flag timing.
- Per-time-control Elo and global/friends/weekly leaderboards.
- Reconnect/resume, SQLite-persisted active rooms and completed games.
- Sanitized/rate-limited chat, reports, friend relationships, and friend spectating.

## Local setup

Use Node.js 20 (the pinned `better-sqlite3` version supports it):

```bash
cd backend
npm ci
cp .env.example .env
# Generate a strong JWT secret; do not use the example value.
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
# Put the generated value in .env, then:
npm start
```

Environment variables:

| Variable | Meaning |
| --- | --- |
| `PORT` | HTTP/WebSocket listening port; default `3000` |
| `JWT_SECRET` | Private random secret, at least 32 characters |
| `DATA_PATH` | SQLite path; default `./data/cheshers.sqlite` |
| `CORS_ORIGIN` | `*` for development or a comma-separated allowlist |
| `TRUST_PROXY` | Set `true` only behind a trusted reverse proxy |

Health check: `GET /health`. Leaderboards: `GET /leaderboard?category=blitz&scope=global`.

For the Android emulator, debug builds default to `http://10.0.2.2:3000`. The debug network-security config permits cleartext only for emulator loopback. Release builds reject cleartext; use HTTPS/WSS.

## Production topology

Run Node on a private port and terminate TLS with Caddy, nginx, or an equivalent proxy. Persist and back up the SQLite file. Restrict file permissions and keep `.env` outside source control.

Example Caddy site (Caddy obtains TLS certificates and forwards WebSocket upgrades):

```caddy
chess.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:3000
}
```

Equivalent nginx location:

```nginx
location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 75s;
}
```

Set `TRUST_PROXY=true` only when requests actually arrive through that proxy. Set `CORS_ORIGIN` to the app/site origins you intend to allow. Native Socket.IO clients do not use browser CORS in exactly the same way, so authentication and input validation remain mandatory.

In Android **Settings → Self-hosted online backend**, enter only the origin, for example:

```text
https://chess.example.com
```

Do not append `/socket.io` or `/leaderboard`. Socket.IO uses secure WebSocket transport through the same origin.

## Tests

```bash
cd backend
npm ci
npm test
```

The harness uses temporary databases and real Socket.IO clients. It covers authentication and game rules plus matchmaking, synchronized post-move FENs, chat, reconnect/resume on a fresh socket, resignation, and rating changes. Tests do not require a deployed server.

## Operational notes

- Use a process manager or systemd for restart-on-failure and controlled shutdown.
- Back up `DATA_PATH`; active rooms are restored after process restart.
- JWT sessions expire after 30 days. Rotate `JWT_SECRET` only with the understanding that existing sessions become invalid.
- Reports are persisted for the operator to review; the repository does not provide a moderation dashboard or automated bans.
- Guest accounts are persistent server records, but a lost guest token cannot be recovered like a registered account.
- Apply OS/dependency updates, rate limits at the proxy as appropriate, and a retention/deletion policy for account, chat, report, and game data.
- This backend is multiplayer infrastructure only. It does not proxy or protect OpenRouter keys.
