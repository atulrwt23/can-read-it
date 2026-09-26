#!/usr/bin/env bash
# Pull-based deploy (docs/deploy.md). canreadit-deploy.timer runs this every 2 minutes.
#
# 1. Fetch origin/main. Nothing to do if that commit is already deployed.
# 2. Wait until CI has published images tagged with the commit SHA (CI only publishes after the
#    tests pass, so red commits never deploy).
# 3. Check out that commit (so compose.yml and scripts match the images), pull, and restart.
# 4. Wait for the api and web health checks. If they fail, roll back to the previous SHA and
#    remember the failed SHA, so the timer does not retry it every 2 minutes (the next commit
#    on main is tried as usual).
#
# Usage:
#   deploy.sh                 deploy origin/main if needed (what the timer runs)
#   deploy.sh --force [<sha>] redeploy even if already deployed or marked failed
#   deploy.sh --pin <sha>     deploy <sha> and hold it: the timer stops following main
#   deploy.sh --unpin         follow origin/main again (and deploy it)
#
# Migrations run when the api starts, and a rollback does not undo them: every migration must
# keep the previous release working (expand, then contract in a later release).
set -euo pipefail

# Everything runs inside main() so bash has parsed the whole file before `git checkout` below
# replaces it on disk.
main() {
  local here force=false target
  here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  # shellcheck source=lib.sh
  . "$here/lib.sh"
  load_env
  mkdir -p "$STATE"
  exec 9>"$STATE/deploy.lock"
  flock -n 9 || { log "another deploy is running"; exit 0; }

  local pin=false
  case "${1:-}" in
    --force) force=true; shift ;;
    --pin)
      [[ -n "${2:-}" ]] || { log "usage: deploy.sh --pin <sha>"; exit 2; }
      force=true; pin=true; shift ;;
    --unpin) rm -f "$STATE/pinned"; force=true; log "unpinned: following origin/main again"; shift ;;
  esac
  if [[ -f "$STATE/pinned" && "$force" == false ]]; then
    exit 0 # pinned by an operator (deploy.sh --unpin to resume)
  fi
  git -C "$REPO" fetch --quiet origin main
  target=$(git -C "$REPO" rev-parse "${1:-origin/main}")
  local current
  current=$(deployed_tag)
  if [[ "$force" == false ]]; then
    [[ "$target" == "$current" ]] && exit 0
    grep -qx "$target" "$STATE/failed" 2> /dev/null && exit 0
  fi

  local svc
  for svc in api web; do
    if ! docker buildx imagetools inspect "$IMAGE_REGISTRY/can-read-it-$svc:$target" > /dev/null 2>&1; then
      log "images for ${target:0:7} are not published yet; will retry"
      exit 0
    fi
  done

  log "deploying ${target:0:7} (currently ${current:0:7})"
  if release "$target"; then
    echo "$target" > "$STATE/deployed"
    if $pin; then
      echo "$target" > "$STATE/pinned"
      log "deployed and pinned ${target:0:7}; the timer will not replace it (deploy.sh --unpin)"
    else
      log "deployed ${target:0:7}"
    fi
    docker image prune --force --filter "until=168h" > /dev/null
    return 0
  fi

  log "${target:0:7} is unhealthy; it will not be retried automatically (deploy.sh --force $target)"
  echo "$target" >> "$STATE/failed"
  compose logs --tail 80 api web || true
  if [[ -n "$current" ]]; then
    log "rolling back to ${current:0:7}"
    release "$current" || log "rollback to ${current:0:7} is unhealthy too: needs a human"
  fi
  exit 1
}

release() {
  local tag=$1
  git -C "$REPO" checkout --quiet --detach "$tag"
  export IMAGE_TAG=$tag
  compose pull --quiet api web
  compose up --detach --remove-orphans
  wait_healthy api web
}

# The first start of a fresh demo database seeds and uploads placeholder images, so allow minutes.
wait_healthy() {
  local deadline=$((SECONDS + ${HEALTH_TIMEOUT:-600})) svc id status all
  while (( SECONDS < deadline )); do
    all=true
    for svc in "$@"; do
      id=$(compose ps --quiet "$svc")
      status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null || echo missing)
      [[ "$status" == healthy ]] || all=false
    done
    if $all; then return 0; fi
    sleep 5
  done
  return 1
}

main "$@"
