#!/usr/bin/env bash
# Prints the public address of the quick tunnel ($0 mode). It changes whenever the
# cloudflared-quick container restarts (VM reboot, image update).
set -euo pipefail
hostname=$(curl -fsS http://127.0.0.1:2000/quicktunnel | sed -n 's/.*"hostname":"\([^"]*\)".*/\1/p')
if [[ -z "$hostname" ]]; then
  echo "No quick tunnel yet. Is the cloudflared-quick container running? (COMPOSE_PROFILES=quick)" >&2
  exit 1
fi
echo "https://$hostname"
