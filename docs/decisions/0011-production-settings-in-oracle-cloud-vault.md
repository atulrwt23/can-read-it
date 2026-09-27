# 0011. Production settings in Oracle Cloud Vault

**Status:** Accepted (2026-09-26). Refines the "secrets only on the VM" part of [0010](0010-pull-based-deploys-and-quick-tunnel.md).

## Context
Production settings (R2 keys, the database password, tunnel mode and so on) lived in `/opt/canreadit/.env` on the VM, with the owner's password manager as the only other copy. We want them in one managed place that scripts can fetch, with history, without storing a credential on the VM, and still for $0.

## Decision
- All production settings live in **one Oracle Cloud Vault secret** (`canreadit-prod-env`) whose content is the env file (`KEY=value` lines, as in `infra/prod/.env.example`).
- The VM reads it with its **instance principal** (its own Oracle identity), through a dynamic group containing just that instance and a policy allowing `read secret-bundles` on just that secret. No API key or token is stored on the VM.
- `infra/prod/lib.sh` resolves settings per `SECRETS_PROVIDER` in `/opt/canreadit/secrets.conf` (not secret):
  - `oci-vault` fetches the secret on every deploy or backup run into `/run/canreadit/env`, which is memory only, root only, and gone on reboot.
  - `file` keeps using `/opt/canreadit/.env`.
- If Vault can't be read, deploys and backups stop with a clear message. Running containers keep running, because they already have their settings.
- The application is unchanged: it still reads environment variables.

## Consequences
- Settings are edited in the Oracle console, and every change is a new secret version that can be rolled back. A change takes effect with `deploy.sh --force`.
- No bootstrap secret to leak: copying the VM's disk yields no credentials.
- Oracle-specific, but only inside `lib.sh`. On another platform the same shape maps to a cloud secrets manager plus the machine's identity (for example AWS Secrets Manager plus an instance role), which is one new provider in `lib.sh`.
- Each deploy tick (every 2 minutes) makes one Vault read, well within free limits.
