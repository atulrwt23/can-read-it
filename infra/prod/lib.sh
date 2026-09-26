# shellcheck shell=bash
# Shared helpers for the ops scripts in this directory. Sourced, not executed.
CANREADIT_ROOT=${CANREADIT_ROOT:-/opt/canreadit}
REPO=${REPO:-$CANREADIT_ROOT/repo}
STATE=${STATE:-$CANREADIT_ROOT/state}
# Says where production settings come from (not secret itself). See docs/deploy.md.
SECRETS_CONF=${SECRETS_CONF:-$CANREADIT_ROOT/secrets.conf}
# Fetched secrets live here: tmpfs, root only, gone on reboot.
RUNTIME_DIR=${RUNTIME_DIR:-/run/canreadit}
ENV_FILE=${ENV_FILE:-$CANREADIT_ROOT/.env}

log() { echo "$(date -u +%FT%TZ) $*"; }

# Points ENV_FILE at the production settings for the configured provider:
#   file       /opt/canreadit/.env (chmod 600)
#   oci-vault  one Oracle Cloud Vault secret holding the same KEY=value lines, read with the
#              VM's own identity (instance principal), so no credential is stored on the VM
resolve_env_file() {
  local SECRETS_PROVIDER=file OCI_SECRET_ID=""
  if [[ -r "$SECRETS_CONF" ]]; then
    # shellcheck disable=SC1090
    . "$SECRETS_CONF"
  fi
  case "$SECRETS_PROVIDER" in
    file) ;;
    oci-vault) fetch_oci_vault_env "$OCI_SECRET_ID" ;;
    *) log "unknown SECRETS_PROVIDER '$SECRETS_PROVIDER' in $SECRETS_CONF (use file or oci-vault)"; exit 1 ;;
  esac
}

fetch_oci_vault_env() {
  local secret_id=$1 tmp
  [[ -n "$secret_id" ]] || { log "OCI_SECRET_ID is empty in $SECRETS_CONF"; exit 1; }
  command -v oci > /dev/null || { log "the oci CLI is not installed (re-run bootstrap.sh)"; exit 1; }
  install -d -m 700 "$RUNTIME_DIR"
  tmp=$(mktemp "$RUNTIME_DIR/env.XXXXXX")
  if ! oci secrets secret-bundle get --secret-id "$secret_id" --auth "${OCI_CLI_AUTH:-instance_principal}" \
      --query 'data."secret-bundle-content".content' --raw-output 2> "$RUNTIME_DIR/oci.log" \
      | base64 -d > "$tmp" || [[ ! -s "$tmp" ]]; then
    rm -f "$tmp"
    log "could not read the settings from Oracle Cloud Vault: $(tail -n 1 "$RUNTIME_DIR/oci.log")"
    log "check the dynamic group and policy (docs/deploy.md). Running containers are unaffected."
    exit 1
  fi
  chmod 600 "$tmp"
  mv "$tmp" "$RUNTIME_DIR/env"
  ENV_FILE="$RUNTIME_DIR/env"
}

load_env() {
  resolve_env_file
  [[ -r "$ENV_FILE" ]] || { log "missing $ENV_FILE (copy infra/prod/.env.example)"; exit 1; }
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
}

compose() {
  docker compose -f "$REPO/infra/prod/compose.yml" --env-file "$ENV_FILE" "$@"
}

deployed_tag() {
  cat "$STATE/deployed" 2>/dev/null || true
}
