# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

**HydraManagerBot** — a Kotlin / Spring Boot 4.x Telegram bot for AI-based message
processing and impersonation. It uses Gemini (with a Groq fallback), persists to
MongoDB, and caches in Redis. See `AGENTS.md` for the service-by-service breakdown
of the application code.

## Commands

```bash
./gradlew build              # compile + package the fat jar (tests skipped in CI)
./gradlew build -x test      # same, explicitly skipping tests (what the Docker build runs)
./gradlew bootRun            # run locally (needs MongoDB + Redis + env vars)
docker compose up -d         # run app + MongoDB + Redis together
```

## Deployment

Deploy is **push-to-deploy via GitHub Actions** — there is no manual deploy step in
normal use. `.github/workflows/deploy.yml` runs on **every push except `master`**
(the active branch is `ManagerBot_oc`). Two jobs:

1. **build-and-push** — builds the multi-stage Docker image and pushes it to **GHCR**
   as `ghcr.io/<owner>/<repo>:latest` (owner/repo lowercased). `no-cache: true`, so
   every build is from scratch — slow deploys are by design, not a hang.
2. **deploy** (needs build-and-push) — ships to the VPS over SSH/SCP:
   - Writes `.env` from GitHub secrets.
   - `ssh`: `mkdir -p /opt/hydra-manager-bot/data/logs` + chown to the deploy user.
   - `scp`: copies `docker-compose.yml` and `.env` to `/opt/hydra-manager-bot/`.
   - `ssh`: `docker login ghcr.io`, write `cookies.txt`, then
     `docker compose pull && down && up -d`. The image is **pulled** from GHCR — the
     VPS does not rebuild.

### Runtime topology (docker-compose.yml)

Three containers on the VPS, all `restart: always`:

| Service | Image | Ports | Notes |
|---|---|---|---|
| `app` (`hydra-manager-bot`) | GHCR image | `8086:8080` | depends on healthy mongo + redis; volume `./data:/data` |
| `mongodb` (`hydra-mongodb`) | `mongo:7` | `127.0.0.1:27018:27017` | volume `mongodb_data`; `mongosh` ping healthcheck |
| `redis` (`hydra-redis`) | `redis:alpine` | — | volume `redis_data` |

App env inside the container: `REDIS_HOST=redis`,
`MONGODB_URI=mongodb://mongodb:27017/hydramanagerbot`, `AI_PROVIDER=gemini`, plus
secrets passed through from `.env`.

### Image build (Dockerfile)

Multi-stage on `eclipse-temurin:25`: builder runs `./gradlew dependencies` (cached
layer) then `./gradlew build -x test`; runtime is JRE-alpine and copies
`build/libs/hydra-manager-bot-1.0-SNAPSHOT.jar` → `app.jar`. The jar name is
**hardcoded** in the `COPY` line — if `version`/`name` in `build.gradle.kts` changes,
update the Dockerfile or the image won't build. (`startup.sh` references the same jar
and starts Xvfb first, but it is **not** the Docker `ENTRYPOINT`.)

### Required GitHub secrets

- **VPS access**: `HOST`, `USERNAME`, `SSH_PRIVATE_KEY`
- **Bot / AI**: `BOT_NAME`, `HYDRA_BOT_TOKEN`, `PAID_GOOGLE_API_KEY`, `GROQ_API_KEY`
- **GitHub dispatch**: `PAT` (workflow hardcodes `GITHUB_REPO=linkfixerKotlin`,
  `GITHUB_OWNER=hteariH`, `GITHUB_REF=ManagerBot_oc` into `.env`)
- **Misc**: `COOKIES_CONTENT` (written to `cookies.txt`), `GITHUB_TOKEN` (auto, for GHCR login)

Adding a runtime env var requires editing it in **three places**: the `Create .env file`
step in `deploy.yml`, the `environment:` block in `docker-compose.yml`, and (if
sensitive) the GitHub secrets.

### Manual ops on the VPS

From `/opt/hydra-manager-bot`:

```bash
sudo docker compose pull          # get latest GHCR image
sudo docker compose up -d         # recreate changed containers
sudo docker compose logs -f app   # tail app logs
sudo docker compose ps            # health/status
```

### Gotchas

- Pushing to `master` does **not** deploy (`branches-ignore: master`).
- `.github/workflows/opencode-bot.yml` is a `workflow_dispatch` agent runner —
  unrelated to deployment.