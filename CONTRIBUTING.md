# Contributing

Thanks for considering a contribution to Omni Data Panel.

## Before you start

1. Search existing Issues / PRs to avoid duplicates.
2. For large changes, open an Issue first to align on scope.
3. Security vulnerabilities: follow [SECURITY.md](SECURITY.md) — do **not** file a public Issue.

## Development setup

See [README.md](README.md) for JDK 21 / Node 22 / Compose prerequisites.

```bash
# backend
cd server && ./mvnw clean verify

# frontend
cd web && npm ci && npm test && npm run build
```

Optional Docker Testcontainers: `./mvnw -Domni.test.docker=true test` (from `server/`).

## Pull requests

- Keep PRs focused; avoid drive-by refactors unrelated to the change.
- Match existing code style and naming (Chinese comments/log messages are used in this repo).
- Include tests when changing behavior (server unit/contract tests; web Vitest when UI logic changes).
- Update docs under `docs/` when user-facing behavior or APIs change (`/help` renders those files).
- Do not commit secrets, local `.env`, or `server/target` / `web/dist` build artifacts.

### Suggested commit style

Short imperative summary in Chinese or English, e.g. `fix embed JWT locked parameters` / `补全 Apache-2.0 开源基建`.

## Code of conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
