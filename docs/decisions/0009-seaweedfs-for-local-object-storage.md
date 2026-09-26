# 0009. SeaweedFS for local object storage

**Status:** Accepted (2026-09-26)

## Context
The original plan used MinIO as the local stand-in for Cloudflare R2. MinIO has stopped publishing community Docker images (the `minio/minio` and `minio/mc` images are no longer pullable from Docker Hub), so a fresh checkout could not start the local stack.

## Decision
- Use **SeaweedFS** (`chrislusf/seaweedfs`, Apache-2.0) in single-container mode (`weed server -s3`) as the local S3-compatible store, on port 8333.
- Access keys and anonymous public read are defined in `infra/local/seaweedfs/s3.json`. They are local-only development credentials.
- A one-shot `amazon/aws-cli` container creates the media bucket.
- The application talks to it only through the S3 API (`ObjectStorage`, see [0002](0002-free-tier-first-vendor-neutral-seams.md)), with path-style addressing and checksums sent only when required, which also suits R2.

## Consequences
- No application code depends on the choice; replacing SeaweedFS again is a compose-file change.
- There is no bundled web console like MinIO's. Use the AWS CLI or any S3 browser against `http://localhost:8333`.
- Local public image URLs look like `http://localhost:8333/canreadit-media/<key>`.
