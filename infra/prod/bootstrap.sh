#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 (arm64 or amd64) VM. Run as root:
#   curl -fsSL https://raw.githubusercontent.com/atulrwt23/can-read-it/main/infra/prod/bootstrap.sh | bash
# Safe to re-run. Afterwards: put the settings in Oracle Cloud Vault (or /opt/canreadit/.env),
# then `systemctl start canreadit-deploy`. See docs/deploy.md.
set -euo pipefail
REPO_URL=${REPO_URL:-https://github.com/atulrwt23/can-read-it.git}
ROOT=/opt/canreadit

apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q ca-certificates curl git pipx unattended-upgrades
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

# Oracle's CLI, used to read settings from Oracle Cloud Vault with the VM's own identity.
if ! command -v oci > /dev/null; then
  PIPX_HOME=/opt/pipx PIPX_BIN_DIR=/usr/local/bin pipx install --quiet oci-cli
fi

mkdir -p "$ROOT/state"
if [[ ! -d "$ROOT/repo/.git" ]]; then
  git clone --quiet "$REPO_URL" "$ROOT/repo"
fi
if [[ ! -f "$ROOT/secrets.conf" ]]; then
  cat > "$ROOT/secrets.conf" <<'CONF'
# Where deploy.sh and backup.sh read the production settings (docs/deploy.md). Not secret.
#   file       /opt/canreadit/.env (chmod 600)
#   oci-vault  the Oracle Cloud Vault secret below, read with the VM's own identity
SECRETS_PROVIDER=file
OCI_SECRET_ID=
CONF
  chmod 644 "$ROOT/secrets.conf"
fi
if [[ ! -f "$ROOT/.env" ]] && grep -q '^SECRETS_PROVIDER=file' "$ROOT/secrets.conf"; then
  install -m 600 "$ROOT/repo/infra/prod/.env.example" "$ROOT/.env"
  echo "Created $ROOT/.env from the example (used only while SECRETS_PROVIDER=file)."
fi

install -m 644 "$ROOT"/repo/infra/prod/systemd/canreadit-*.{service,timer} /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now canreadit-deploy.timer canreadit-backup.timer
echo "Done. Next: settings in Oracle Cloud Vault (docs/deploy.md), then:"
echo "  $ROOT/repo/infra/prod/secrets.sh check && systemctl start canreadit-deploy && journalctl -fu canreadit-deploy"
