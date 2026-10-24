# Cheshers backend

Self-hosted Node.js 20 / Express / Socket.IO / SQLite multiplayer service. No public instance is deployed with this repository.

```bash
npm ci
cp .env.example .env
# Replace JWT_SECRET with a strong random value, then:
npm test
npm start
```

See [`../docs/BACKEND.md`](../docs/BACKEND.md) for environment variables, emulator access, production HTTPS/WSS proxy configuration, persistence, security, and test coverage. Licensed AGPL-3.0-or-later; third-party notices are in [`../docs/THIRD_PARTY_NOTICES.md`](../docs/THIRD_PARTY_NOTICES.md).
