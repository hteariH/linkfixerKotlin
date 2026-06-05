---
name: deploy
description: How HydraManagerBot is built and deployed (GHCR image + SSH/SCP to a VPS via .github/workflows/deploy.yml, run with docker-compose). Use when deploying, debugging a failed deploy, changing the CI/CD pipeline, adding env vars/secrets, or operating the running containers on the VPS.
---

# Deploying HydraManagerBot

This bot deploys automatically via GitHub Actions. There is **no manual deploy step** in normal use — pushing a branch ships it.

## Pipeline at a glance

`.github/workflows/deploy.yml` runs on **every push except `master`**. Two jobs:

1. **build-and-push** — builds the Docker image and pushes it to GHCR.
   - Image: `ghcr.io/<owner>/<repo>:latest` (owner/repo lowercased into `IMAGE_REPO`).
   - `no-cache: true`, so every build is from scratch.
2. **deploy** (needs build-and-push) — ships to the VPS over SSH:
   - Writes `.env` from GitHub secrets (see below).
   - `ssh`: `sudo mkdir -p /opt/hydra-manager-bot/data/logs` and chown to the deploy user.
   - `scp`: copies `docker-compose.yml` and `.env` to `/opt/hydra-manager-bot/`.
   - `ssh`: `docker login ghcr.io`, write `cookies.txt`, then `docker compose pull && down && up -d`.

The image is pulled from GHCR on the VPS — `docker-compose.yml` references `${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}` and does not rebuild there.

## Runtime topology (docker-compose.yml)

Three services on the VPS, all `restart: always`:

| Service | Image | Ports | Notes |
|---|---|---|---|
| `app` (`hydra-manager-bot`) | GHCR image | `8086:8080` | depends on healthy mongo + redis; volume `./data:/data` |
| `mongodb` (`hydra-mongodb`) | `mongo:7` | `127.0.0.1:27018:27017` | volume `mongodb_data`; healthcheck via `mongosh` ping |
| `redis` (`hydra-redis`) | `redis:alpine` | — | volume `redis_data` |

App container env: `REDIS_HOST=redis`, `MONGODB_URI=mongodb://mongodb:27017/hydramanagerbot`, `AI_PROVIDER=gemini`, plus secrets passed through from `.env`.

## Image build (Dockerfile)

Multi-stage on `eclipse-temurin:25`:
- **builder**: copies Gradle wrapper + build files first (cached dep layer via `./gradlew dependencies`), then `./gradlew build -x test`.
- **runtime**: JRE-alpine, copies `build/libs/hydra-manager-bot-1.0-SNAPSHOT.jar` → `app.jar`, `ENTRYPOINT ["java","-jar","app.jar"]`.

The jar name is hardcoded — if `version`/`name` in `build.gradle.kts` changes, **update the `COPY` line in `Dockerfile`** or the image won't build. (`startup.sh` references the same jar under `build/libs/` and starts Xvfb first; it is not used by the Docker `ENTRYPOINT`.)

## Required GitHub secrets

Set under repo Settings → Secrets and variables → Actions:

- **VPS access**: `HOST`, `USERNAME`, `SSH_PRIVATE_KEY`
- **Bot / AI**: `BOT_NAME`, `HYDRA_BOT_TOKEN`, `PAID_GOOGLE_API_KEY`, `GROQ_API_KEY`
- **GitHub dispatch integration**: `PAT` (the workflow also hardcodes `GITHUB_REPO=linkfixerKotlin`, `GITHUB_OWNER=hteariH`, `GITHUB_REF=ManagerBot_oc` into `.env`)
- **Misc**: `COOKIES_CONTENT` (written to `cookies.txt` on the VPS), `GITHUB_TOKEN` (auto-provided, used for GHCR login)

If you add a new runtime env var, add it in **three places**: the `Create .env file` step in `deploy.yml`, the `environment:` block in `docker-compose.yml`, and as a GitHub secret if it's sensitive.

## How to deploy

- **Normal**: push the branch (anything but `master`). CI builds + ships automatically. Watch with `gh run watch` / `gh run list`.
- **Manual on the VPS** (hotfix / debugging), from `/opt/hydra-manager-bot`:
  ```bash
  sudo docker compose pull        # get latest GHCR image
  sudo docker compose up -d       # recreate changed containers
  sudo docker compose logs -f app # tail app logs
  sudo docker compose ps          # health/status
  ```

## Gotchas

- Pushing to `master` does **not** deploy (`branches-ignore: master`). Active branch is `ManagerBot_oc`.
- `no-cache: true` means full rebuilds every time — deploys are slow by design, not hung.
- A separate workflow, `opencode-bot.yml`, is a `workflow_dispatch` agent runner — unrelated to deployment.
