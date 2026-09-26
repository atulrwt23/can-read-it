# media

**Purpose.** Stores images and is the only module that turns asset IDs into URLs ([ADR 0004](../decisions/0004-media-url-resolver-and-asset-visibility.md)).

## Public API (`com.canreadit.media`)
- `MediaUrls.resolve(ids, variant)`: one batched lookup; only `READY` + `PUBLIC` assets resolve. `PROTECTED` assets (early access) will get signed URLs in phase 3.
- `MediaUploads.storePublicImage(kind, bytes)`: validates by magic bytes (PNG, JPEG, WebP; never SVG), reads dimensions server-side, stores under an immutable content-addressed key (`<kind>s/<sha[0:2]>/<sha256>.<ext>`) with `Cache-Control: public, max-age=31536000, immutable`, and deduplicates identical content.
- Records and enums: `ImageRef` (URL, width, height), `AssetKind`, `ImageVariant`.

## Data (schema `media`)
`assets`: kind, visibility, storage key, sha256, content type, size, status, variants (jsonb).

## External port
`ObjectStorage` with the `S3ObjectStorage` adapter (AWS SDK v2 on the JDK HTTP client, path-style, checksums only when required). Works with SeaweedFS locally and Cloudflare R2 in the beta; configured under `app.media.*`.

## Open questions
- `TODO(step 3)`: the upload pipeline re-encodes to WebP (which also strips metadata) and produces cover sizes.
- `TODO(product)`: whether creators need their original files back.
