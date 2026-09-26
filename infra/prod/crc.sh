#!/usr/bin/env bash
# `docker compose` for the production stack, with the settings loaded the same way the deploy
# does and the image tag pinned to the deployed release. Examples (docs/deploy.md):
#   crc.sh ps    crc.sh logs -f --tail 100 api    crc.sh exec postgres psql -U canreadit
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"
load_env
export IMAGE_TAG=${IMAGE_TAG:-$(deployed_tag)}
compose "$@"
