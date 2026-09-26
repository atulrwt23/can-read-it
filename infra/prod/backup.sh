#!/usr/bin/env bash
# Nightly PostgreSQL backup to a private R2 bucket (run by canreadit-backup.timer).
# Expire old dumps with an R2 lifecycle rule on the bucket (docs/deploy.md). Restore steps are in
# docs/deploy.md; rehearse them before the beta opens.
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"
load_env
export IMAGE_TAG=${IMAGE_TAG:-$(deployed_tag)}

key="postgres/canreadit-$(date -u +%Y%m%dT%H%M%SZ).dump"
dump=$(mktemp /tmp/canreadit-backup.XXXXXX)
trap 'rm -f "$dump"' EXIT

log "dumping database"
compose exec -T postgres pg_dump --format=custom --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" > "$dump"

# Upload a file of known size, with checksums only where required: the widest compatibility
# across S3-compatible stores (R2, SeaweedFS).
log "uploading $(du -h "$dump" | cut -f1) to s3://$BACKUP_BUCKET/$key"
docker run --rm \
  -v "$dump:/backup.dump:ro" \
  -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
  -e AWS_DEFAULT_REGION=auto \
  -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
  -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
  amazon/aws-cli:2.37.4 --endpoint-url "$BACKUP_S3_ENDPOINT" s3 cp --only-show-errors /backup.dump "s3://$BACKUP_BUCKET/$key"
log "backup done"
