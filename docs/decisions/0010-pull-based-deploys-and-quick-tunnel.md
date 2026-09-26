# 0010. Pull-based deploys, and a quick tunnel until there is a domain

**Status:** Accepted (2026-09-26). Supersedes the "deploy over SSH through Cloudflare Access" and "SOPS-encrypted env in git" parts of CLAUDE.md §9.

## Context
The beta must cost $0 and there is no domain yet. Without a domain there is no named Cloudflare Tunnel, no Cloudflare Access in front of SSH and no R2 custom domain. Pushing deploys from GitHub Actions would mean opening SSH to GitHub's changing IP ranges and storing an SSH key and the production secrets in CI. The repository is public.

## Decision
- **Pull-based deploys.** A systemd timer on the VM runs `infra/prod/deploy.sh` every 2 minutes:
  - it deploys `origin/main` once CI has published images tagged with that commit SHA
  - it waits for health checks
  - it rolls back and remembers the SHA if the release is unhealthy
  - operators can pin a release
- CI publishes the `:<sha>` images only after the tests pass, on native amd64 and arm64 runners.
- **Secrets live only on the VM** in `/opt/canreadit/.env` (chmod 600), with a copy in the owner's password manager. Nothing secret is committed, encrypted or not, and CI holds no production secrets.
- **Quick tunnel while there is no domain:** `cloudflared` forwards everything to `web`, and Next.js proxies `/api/*` to the API. Images are served from R2's `r2.dev` address.
- **Moving to a domain** is configuration only: `COMPOSE_PROFILES=named`, a tunnel token, an R2 custom domain. See docs/deploy.md.

## Consequences
- The VM exposes no inbound ports for the application, and CI needs no credentials for it.
- Releases go live about 2 minutes after images are published; a deploy restarts the API for about 10 seconds.
- The quick-tunnel address changes when `cloudflared` restarts, and `r2.dev` is rate-limited, which is acceptable for a private beta but not for launch.
- Migrations must stay compatible with the previous release, because rollbacks don't undo them.
