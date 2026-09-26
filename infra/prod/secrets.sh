#!/usr/bin/env bash
# Checks the production settings without printing any value (docs/deploy.md).
#   secrets.sh check   fetch from the configured provider and report missing or empty settings
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

case "${1:-check}" in
  check)
    load_env
    provider=$(sed -n 's/^SECRETS_PROVIDER=//p' "$SECRETS_CONF" 2> /dev/null || true)
    log "settings read from ${provider:-file} ($ENV_FILE)"
    # Every variable named in .env.example must be present; some may legitimately be empty.
    optional="TUNNEL_TOKEN COMPOSE_PROFILES"
    missing=0
    while IFS= read -r name; do
      if [[ -z "${!name:-}" && " $optional " != *" $name "* ]]; then
        log "missing or empty: $name"
        missing=$((missing + 1))
      fi
    done < <(sed -n 's/^\([A-Z_][A-Z0-9_]*\)=.*/\1/p' "$here/.env.example")
    if ((missing > 0)); then
      log "$missing setting(s) need a value"
      exit 1
    fi
    log "all settings present"
    ;;
  *)
    echo "usage: secrets.sh check" >&2
    exit 2
    ;;
esac
