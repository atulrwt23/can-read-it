# shellcheck shell=bash
# Shared helpers for the ops scripts in this directory. Sourced, not executed.
CANREADIT_ROOT=${CANREADIT_ROOT:-/opt/canreadit}
REPO=${REPO:-$CANREADIT_ROOT/repo}
ENV_FILE=${ENV_FILE:-$CANREADIT_ROOT/.env}
STATE=${STATE:-$CANREADIT_ROOT/state}

log() { echo "$(date -u +%FT%TZ) $*"; }

load_env() {
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
