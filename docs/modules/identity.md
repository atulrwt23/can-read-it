# identity

**Purpose.** Accounts, sign-in methods, sessions and roles (CLAUDE.md section 7). Step 2a, built so far: email and password sign-up and sign-in, sessions, token refresh, sign-out and `GET /me`. Email codes (2b), Google and Apple (2c), account management (2d) and the web pages (2e) follow.

## Endpoints
| Route | Notes |
|---|---|
| `POST /api/v1/auth/password/register` | `{ email, password, handle, botToken? }` → `201` with the user, and the session cookies. `409 email_taken`, `409 handle_taken`, `400 bot_check_failed` |
| `POST /api/v1/auth/password/login` | `{ email, password }` → `200` with the user, and the session cookies. `401 invalid_credentials` for a wrong password and an unknown email alike, `403 account_unavailable` for a suspended or banned account, `429` while backing off |
| `POST /api/v1/auth/refresh` | the refresh cookie → `204` with new cookies. `401 invalid_refresh` clears the cookies; `409 refresh_superseded` means another tab just refreshed (retry with the new cookie) |
| `POST /api/v1/auth/logout` | ends the session of the access cookie. Needs the CSRF header; always `204` |
| `GET /api/v1/me` | the signed-in user (`id`, `handle`, `displayName`, `email`, `emailVerified`, `roles`, `createdAt`). `401` without a valid token |
| `GET /.well-known/jwks.json` | the public key that verifies access tokens. Cached 5 minutes |

Every response here is `Cache-Control: no-store`. Handles are 3–30 letters, digits or underscores, unique ignoring case; the display name starts as the handle (`TODO(product)`). Passwords are 10–128 characters, with no composition rules.

## Tokens and cookies
- **Access token:** an ES256 JWT, 15 minutes (`app.auth.access-token-ttl`). Claims `iss`, `sub` (user), `sid` (session), `roles`, `ev` (email verified), `iat`, `exp`, and `kid` in the header (the key's RFC 7638 thumbprint). The key is `AUTH_JWT_PRIVATE_KEY` (base64 PKCS#8 P-256); without one the API makes a new key at startup and logs a warning ([deploy.md](../deploy.md#sign-in-settings)).
- **Refresh token:** 32 random bytes, stored as a SHA-256 hash, 30 days (`app.auth.refresh-token-ttl`), sliding: every refresh spends it, issues a new one and extends the session. A spent token presented again within 20 seconds is a race between tabs (`409`, the session survives); later, it is a stolen copy, so the whole session is revoked and `REFRESH_REUSED` is logged.
- **Cookies:** `access_token` (path `/`) and `refresh_token` (path `/api/v1/auth/refresh`), both `httpOnly`, `SameSite=Lax`, and `Secure` except in the `local` profile (`app.cookies.secure`).
- **Bearer header:** `Authorization: Bearer <access token>` works too (for mobile later). `TODO(identity)`: mobile clients will get the tokens in the response body.

## How requests are authenticated
`shared.internal.SecurityConfig` turns the token into the principal and checks it, so other modules only register routes:
- `AuthenticatedEndpoints` beans list routes that need a signed-in user. `PublicGetEndpoints` and `PublicPostEndpoints` list open ones; everything else is denied.
- The token comes from the bearer header, or else the access cookie. Public routes ignore the cookie, so an expired cookie never breaks a public page.
- **CSRF (double submit):** a request authenticated by cookie must echo the `XSRF-TOKEN` cookie (readable by scripts) in the `X-XSRF-TOKEN` header. Requests with an `Authorization` header skip the check. Register, login and refresh skip it too: they take JSON bodies (no cross-site form can send them) and the refresh cookie is `SameSite=Lax`. Logout needs it (`PublicPostEndpoints.withCsrf`).
- **Revocation:** signing out and refresh-token reuse write the session ID to Redis (`auth:revoked:<sid>`) for one access-token lifetime, and the JWT decoder rejects tokens of those sessions. If Redis is down the check is skipped and access tokens stay valid until they expire (15 minutes at most).

## Guardrails
- **Password hashing:** Argon2id (Spring Security defaults). An unknown email still runs a hash, so timing reveals nothing.
- **Login backoff:** after 5 failed logins for one email within 15 minutes, logins for it are locked for 30 seconds, doubling with each further failure up to 15 minutes (`429` with `Retry-After`). A successful login clears it. Never permanent.
- **Rate limits** (`shared.RateLimiter`, per IP unless noted): register 10 an hour; login 30 per 15 minutes, and 10 per 15 minutes per email; refresh 120 a minute.
- **Bot check:** `BotChallengeVerifier`. `TurnstileVerifier` checks the `botToken` with Cloudflare when `TURNSTILE_SECRET_KEY` is set, and refuses the sign-up if Cloudflare can't be reached; without the secret (locally) the check is off.
- **Audit:** `auth_events` records `REGISTERED`, `SIGNED_IN`, `SIGN_IN_FAILED` (with the user when the email exists), `SIGNED_OUT` and `REFRESH_REUSED`, with IP and user agent. A failed login is written in its own transaction, so it survives the error.

## Events
`UserRegistered(userId, handle, registeredAt)`. The rest from CLAUDE.md section 7 arrive with the flows that cause them.

## Data (schema `identity`)
All tables come from the step 1 migration. 2a writes `users`, `credentials`, `user_roles` (every new user is a `READER`), `sessions`, `refresh_tokens` and `auth_events`. The `identity` module is the first to write through JPA; its entities implement `Persistable`, so app-generated UUIDv7 IDs insert without a select first.

## Open questions
- `TODO(product)`: registering with an email that already has an account answers `409 email_taken`, which tells a stranger the email is registered (section 7 rules this out). With email codes (2b), registration will answer the same for new and existing emails, and the owner of an existing account gets an email instead.
- `TODO(product)`: how long to keep `auth_events` (default 180 days; the cleanup job comes with 2b).
- Staff roles will need TOTP and recent re-authentication (`Role`, seam only).
