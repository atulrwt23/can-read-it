#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 (arm64 or amd64) VM. Run as root:
#   curl -fsSL https://raw.githubusercontent.com/atulrwt23/can-read-it/main/infra/prod/bootstrap.sh | bash
# Safe to re-run. Afterwards: fill /opt/canreadit/.env, then `systemctl start canreadit-deploy`.
set -euo pipefail
REPO_URL=${REPO_URL:-https://github.com/atulrwt23/can-read-it.git}
ROOT=/opt/canreadit

apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q ca-certificates curl git unattended-upgrades
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

mkdir -p "$ROOT/state"
if [[ ! -d "$ROOT/repo/.git" ]]; then
  git clone --quiet "$REPO_URL" "$ROOT/repo"
fi
if [[ ! -f "$ROOT/.env" ]]; then
  install -m 600 "$ROOT/repo/infra/prod/.env.example" "$ROOT/.env"
  echo "Created $ROOT/.env from the example: fill it in before the first deploy."
fi
chmod 600 "$ROOT/.env"

install -m 644 "$ROOT"/repo/infra/prod/systemd/canreadit-*.{service,timer} /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now canreadit-deploy.timer canreadit-backup.timer
echo "Done. Edit $ROOT/.env, then run: systemctl start canreadit-deploy && journalctl -fu canreadit-deploy"
