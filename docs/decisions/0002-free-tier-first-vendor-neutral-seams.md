# 0002. Free tier first, vendor-neutral seams

**Status:** Accepted (2026-09-26)

## Context
The beta must cost $0. We also expect to change providers later (planned: AWS compute for the public launch), and free tiers change their terms. Building deeply against any one vendor would make either of those changes expensive.

## Decision
1. Choose free-tier services for the beta: Oracle Always Free VM, Cloudflare free plan and Tunnel, Cloudflare R2, Resend (free tier), GHCR, GitHub Actions, and optionally Grafana Cloud free.
2. Reach every external service through an **open protocol** behind an **interface owned by the module that needs it**:

   | Concern | Protocol | Interface |
   |---|---|---|
   | Object storage | S3 API | `media.internal.ObjectStorage` |
   | Image URLs | none | `media.MediaUrls` |
   | Email | SMTP | `notifications.EmailSender` |
   | Social login | OIDC ID tokens + JWKS | `identity.internal.IdTokenVerifier` |
   | Bot challenge | HTTPS siteverify | `identity.internal.BotChallengeVerifier` |
   | Compute | OCI images + Docker Compose | none |
   | Edge | HTTP + `Cache-Control` | none |
   | Telemetry | Prometheus / OTLP | Actuator |

3. Domain code never imports vendor SDKs. Protocol clients (AWS SDK S3 client, Jakarta Mail) appear only in adapter classes.
4. Every endpoint, bucket, host and credential is configuration (`app.*` or env vars). Nothing vendor-specific is hard-coded.
5. Every image is built for `linux/amd64` and `linux/arm64`.

## Consequences
- Moving R2 to S3, Resend to SES, Oracle to AWS, or Grafana Cloud to self-hosted is a config change, or at most one adapter class.
- We skip vendor-only conveniences (Resend's REST SDK, Cloudflare Images transformations, Workers-specific APIs) unless a later ADR accepts them behind the same seams.
- Free-tier limits (R2 10 GB, VM 2 OCPU / 12 GB, email quotas) are real constraints. Design choices such as one WebP page variant respect them.
