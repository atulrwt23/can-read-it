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
  local secret_id=$1 tmp error
  [[ -n "$secret_id" ]] || { log "OCI_SECRET_ID is empty in $SECRETS_CONF"; exit 1; }
  # The instance OCID is easy to paste here by mistake; Oracle then answers with a vague error.
  if [[ "$secret_id" != ocid1.vaultsecret.* ]]; then
    log "OCI_SECRET_ID in $SECRETS_CONF must be the secret's OCID (ocid1.vaultsecret…), not '${secret_id:0:24}…'"
    exit 1
  fi
  command -v oci > /dev/null || { log "the oci CLI is not installed (re-run bootstrap.sh)"; exit 1; }
  install -d -m 700 "$RUNTIME_DIR"
  tmp=$(mktemp "$RUNTIME_DIR/env.XXXXXX")
  if ! oci secrets secret-bundle get --secret-id "$secret_id" --auth "${OCI_CLI_AUTH:-instance_principal}" \
      --query 'data."secret-bundle-content".content' --raw-output 2> "$RUNTIME_DIR/oci.log" \
      | base64 -d > "$tmp" || [[ ! -s "$tmp" ]]; then
    rm -f "$tmp"
    # The CLI prints a JSON error; its code and message say what is wrong. Never contains settings.
    error=$(sed -nE 's/^[[:space:]]*"(code|message)": "(.*)",?$/\2/p' "$RUNTIME_DIR/oci.log" | paste -sd ' ' -)
    [[ -n "$error" ]] || error=$(grep -v '^[[:space:]]*$' "$RUNTIME_DIR/oci.log" | tail -n 1)
    log "could not read the settings from Oracle Cloud Vault: ${error:-no error output}"
    case "$error" in
      NotAuthorizedOrNotFound*)
        log "check that the policy names this secret's OCID and the dynamic group names this VM's OCID (docs/deploy.md, step 4)" ;;
      InvalidParameter*) log "check OCI_SECRET_ID in $SECRETS_CONF" ;;
      *) log "check the dynamic group and policy (docs/deploy.md, step 4)" ;;
    esac
    log "full error: $RUNTIME_DIR/oci.log. Running containers are unaffected."
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

# Settings in .env.example that may stay empty, and those only the nightly backup needs.
OPTIONAL_SETTINGS="TUNNEL_TOKEN COMPOSE_PROFILES REVALIDATE_SECRET SITE_URL"
BACKUP_SETTINGS="BACKUP_S3_ENDPOINT BACKUP_BUCKET BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY"

# Prints the names (never values) of empty settings that `app` or `backup` needs, one per line.
# Every name comes from .env.example, so a new setting is checked as soon as it is added there.
missing_settings() {
  local scope=$1 name
  while IFS= read -r name; do
    [[ " $OPTIONAL_SETTINGS " == *" $name "* ]] && continue
    if [[ " $BACKUP_SETTINGS " == *" $name "* ]]; then
      [[ "$scope" == backup ]] || continue
    else
      [[ "$scope" == app ]] || continue
    fi
    [[ -n "${!name:-}" ]] || echo "$name"
  done < <(sed -n 's/^\([A-Z_][A-Z0-9_]*\)=.*/\1/p' "$(dirname "${BASH_SOURCE[0]}")/.env.example")
}

# Fails, naming what is missing, unless every setting `app` or `backup` needs has a value.
require_settings() {
  local missing
  missing=$(missing_settings "$1")
  [[ -z "$missing" ]] && return 0
  log "missing or empty settings: $(paste -sd ' ' - <<< "$missing")"
  return 1
}

compose() {
  docker compose -f "$REPO/infra/prod/compose.yml" --env-file "$ENV_FILE" "$@"
}

deployed_tag() {
  cat "$STATE/deployed" 2>/dev/null || true
}
