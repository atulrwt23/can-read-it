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
    # The backup settings can wait until the backups bucket exists: only the nightly backup needs them.
    if ! require_settings backup; then
      log "backups are not configured yet: the nightly backup is skipped until these have values"
    fi
    if ! require_settings app; then
      log "the app can't be deployed until these have values"
      exit 1
    fi
    log "all settings the app needs are present"
    ;;
  *)
    echo "usage: secrets.sh check" >&2
    exit 2
    ;;
esac
