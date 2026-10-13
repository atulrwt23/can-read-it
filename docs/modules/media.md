# media

**Purpose.** Stores images and is the only module that turns asset IDs into URLs ([ADR 0004](../decisions/0004-media-url-resolver-and-asset-visibility.md)).

## Public API (`com.canreadit.media`)
- `MediaUrls.resolve(ids, variant)`: one batched lookup; only `READY` + `PUBLIC` assets resolve. `PROTECTED` assets (early access) will get signed URLs in phase 3.
- `MediaUploads.storePublicImage(kind, bytes)`: runs the image pipeline below and returns a `READY` asset. Identical uploads return the same asset.
- `MediaAssets.find(ids)`: kind, size and readiness of stored assets, so `catalog` can check an upload before a chapter or series references it.
- Records and enums: `ImageRef` (URL, width, height), `AssetKind`, `ImageVariant` (`ORIGINAL`, `MEDIUM`, `SMALL`; an asset without that variant resolves to its original).

## Image pipeline (`MediaService`, `ImageProcessor`)
1. **Check:** at most 20 MB (`app.media.max-upload-size`); PNG, JPEG or WebP by magic bytes (never SVG); at most 50 megapixels, read from the header before decoding.
2. **Decode** with ImageIO (WebP through the libwebp plugin), at most two images at a time per instance so decoding stays within the API's memory.
3. **Resize** (only ever down): pages to 1080 px wide; covers to 1200 (`ORIGINAL`), 600 (`MEDIUM`) and 300 px (`SMALL`).
4. **Encode** lossy WebP (quality 0.82). Only pixels are copied, so EXIF, GPS and other metadata never reach storage. WebP allows 16,383 px a side; taller pages are refused (`image_too_tall`).
5. **Store** under immutable keys addressed by the source's SHA-256: `<kind>s/<sha[0:2]>/<sha256>.webp`, and `<…>-<width>.webp` for smaller variants, recorded in `variants` (`{"MEDIUM": {"key", "width", "height"}}`). Every object gets `Cache-Control: public, max-age=31536000, immutable`. No original file is kept.

Errors are problem details: `upload_too_large`, `image_too_large` (`413`), `unsupported_image` (`415` or `400`), `image_too_tall` (`400`).

**The WebP codec** is `com.github.usefulness:webp-imageio`: libwebp through JNI, with Linux x86-64 and arm64 builds in the jar. It unpacks its native library to `com.luciad.imageio.webp.tmpdir`. The API container's `/tmp` is mounted `noexec`, so the image points it at its own small `exec` tmpfs (`/var/lib/canreadit-native`, see `backend/Dockerfile` and `infra/prod/compose.yml`).

## Staff upload endpoint
`POST /api/v1/admin/media/{covers|pages}` (multipart field `file`, one image per request, permission `MEDIA_UPLOAD`) answers `201` with `{id, url, width, height}` once the asset is ready. The web admin uploads pages one at a time, which keeps each request well under the edge's body limit (100 MB on Cloudflare's free plan).

## Data (schema `media`)
`assets`: kind, visibility, storage key, sha256, content type, size, status, variants (jsonb).

## External port
`ObjectStorage` with the `S3ObjectStorage` adapter (AWS SDK v2 on the JDK HTTP client, path-style, checksums only when required). Works with SeaweedFS locally and Cloudflare R2 in the beta; configured under `app.media.*`.

## Open questions
- `TODO(product)`: processing runs inside the upload request (a few hundred ms per page). If uploads grow (bulk imports, very large pages), move it to a queue and let assets pass through `PROCESSING`.
- `TODO(product)`: very tall strips (over 16,383 px after resizing) are refused rather than split into several pages.
- JPEG EXIF orientation is not applied: a rotated phone photo stays rotated. Scans and exports rarely carry it.
- `TODO(product)`: whether creators need their original files back.
