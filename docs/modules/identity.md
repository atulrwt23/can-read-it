# identity

**Purpose.** Accounts, sign-in methods, sessions and roles (CLAUDE.md section 7). Built so far: password sign-up and sign-in, sessions, token refresh and sign-out (2a); sign-in with an emailed code, email confirmation and password reset (2b); Google and Apple sign-in and linking (2c); account management (2d). The web pages (2e) follow.

## Endpoints
| Route | Notes |
|---|---|
| `POST /api/v1/auth/password/register` | `{ email, password, handle, botToken? }` → always `202`, whether or not the email has an account. A new account gets a confirmation code by email (the account can already sign in with its password); an existing one gets a notice instead. `409 handle_taken` (handles are public), `400 bot_check_failed` |
| `POST /api/v1/auth/password/login` | `{ email, password }` → `200` with the user, and the session cookies. `401 invalid_credentials` for a wrong password and an unknown email alike, `403 account_unavailable` for a suspended or banned account, `429` while backing off |
| `POST /api/v1/auth/email/start` | `{ email, botToken? }` → always `202`. Emails a sign-in code, or a sign-up code if the email has no account (nothing for a suspended or banned one) |
| `POST /api/v1/auth/email/verify` | `{ email, code, handle? }` → `200` with the user and the session cookies. Takes codes from `email/start` and from registration. A sign-up code creates a verified account, with `handle` or a generated `reader_xxxxxxxx`. `400 invalid_code` (wrong, expired, used or out of attempts), `409 handle_taken` (the code stays usable), `403 account_unavailable` |
| `POST /api/v1/auth/password/forgot` | `{ email, botToken? }` → always `202`. Emails a reset code if the email has an active account |
| `POST /api/v1/auth/password/reset` | `{ email, code, newPassword }` → `200` with the user and cookies. Sets the password (or adds one), ends every session, verifies the email. `400 invalid_code` |
| `POST /api/v1/me/email/verification` | signed in, CSRF header → `202`. Emails a new confirmation code if the email is not verified yet |
| `POST /api/v1/auth/oauth/nonce` | → `200 { nonce }`, a one-time nonce (10 minutes) to pass to the Google or Apple SDK |
| `POST /api/v1/auth/oauth/{provider}` | `google` or `apple`, `{ idToken, nonce }` → `200` with the user and cookies; creates the account on first use. `401 invalid_id_token`, `404 provider_unavailable` (not configured), `503 provider_unreachable` (its keys could not be fetched) |
| `POST /api/v1/me/identities/{provider}` | signed in, CSRF, `{ idToken, nonce }` → `204`. `409 identity_in_use` (linked to another user), `409 provider_already_linked` (another account from that provider is linked) |
| `DELETE /api/v1/me/identities/{provider}` | signed in, CSRF → `204`. `409 last_sign_in_method` unless a password, another provider or a verified email (code sign-in) remains |
| `POST /api/v1/auth/refresh` | the refresh cookie → `204` with new cookies. `401 invalid_refresh` clears the cookies; `409 refresh_superseded` means another tab just refreshed (retry with the new cookie) |
| `POST /api/v1/auth/logout` | ends the session of the access cookie. Needs the CSRF header; always `204` |
| `GET /api/v1/me` | the signed-in user (`id`, `handle`, `displayName`, `email`, `emailVerified`, `roles`, `hasPassword`, `providers`, `createdAt`). `401` without a valid token. Every sign-in answers with the same shape |
| `PATCH /api/v1/me` | CSRF, `{ handle?, displayName? }` → the user. `409 handle_taken`; display names are 1–60 characters without control characters |
| `POST /api/v1/me/password` | CSRF, `{ currentPassword?, newPassword }` → `204`, and every other session ends. Needs the current password if there is one (`403 wrong_password`, 10 checks per 15 minutes), otherwise a sign-in within 10 minutes (`403 reauthentication_required`) |
| `GET /api/v1/me/sessions` | active sessions, newest use first: `id`, `current`, `userAgent`, `ip`, `createdAt`, `lastUsedAt` |
| `DELETE /api/v1/me/sessions/{id}` | CSRF → `204`; signs that device out (this one: also clears its cookies). Another user's session is `404` |
| `POST /api/v1/me/sessions/revoke-all` | CSRF → `204`; signs out every device except this one |
| `DELETE /api/v1/me` | CSRF, `{ currentPassword? }` → `202 { eraseAfter }`. Proves identity like the password change, ends every session, emails a notice; see "Deleting an account" |
| `GET /.well-known/jwks.json` | the public key that verifies access tokens. Cached 5 minutes |

Every response here is `Cache-Control: no-store`. Handles are 3–30 letters, digits or underscores, unique ignoring case; the display name starts as the handle (`TODO(product)`). Passwords are 10–128 characters, with no composition rules.

## Email codes
- 6 digits, valid 10 minutes, 5 guesses (`VerificationCodes`), stored as a SHA-256 hash salted with the row ID. Six digits are easy to reverse from a hash, so the protection is the short life and the attempt limit; the hash just keeps codes out of plain sight.
- Each new code retires the unused ones of its group: sign-in and confirmation codes are one group (both entered at `email/verify`), reset codes another. A sign-in code can't reset a password.
- Purposes: `SIGN_IN` from `email/start`, `VERIFY_EMAIL` from registration or the resend, `PASSWORD_RESET` from `forgot`.
- **Taking back a pre-registered email.** A sign-in code (not a confirmation code) used on an account whose email was never verified removes that account's password and ends its sessions before signing in. Whoever registered it never proved they own the email, and the code's recipient just did: this is section 7's account-linking rule applied to codes. The owner can set a password again with a reset. A confirmation code keeps the password, because it was sent right after that password was set.
- **Delivery.** After the change commits, identity publishes `AccountEmailRequested` (to, kind, code); `notifications` sends it on another thread ([notifications.md](notifications.md)). It is a plain Spring event, not a persisted module event, so the plain code is never written to `modulith.event_publication`. A lost email is not retried; the reader asks for a new code.

## Google and Apple
- **Verification** (`IdTokenVerifier`, `OidcIdTokenVerifier`): Nimbus checks the signature against the provider's JWKS (fetched on first use, cached, refetched for an unknown key ID), the audience against our client IDs, expiry with a minute of clock skew, then the issuer and the nonce. Providers are configuration entries under `app.auth.oidc` (issuers, JWKS URL, client IDs); one without client IDs is off.
- **Nonces** (`OAuthNonces`): issued by us, stored in Redis for 10 minutes and spent on first use, before the token is checked. The token must carry the nonce or its SHA-256 (some SDKs send the hash to the provider). A captured token can't be replayed. Without Redis, social sign-in answers `503` rather than skipping the check.
- **Matching** (`SocialSignInService`), always on (provider, subject) first:
  1. A linked identity signs in to its user. The provider email is updated when present (Apple sends it only on the first sign-in).
  2. Otherwise, if the provider says the email is verified and an account has it: if that account's email is verified, the provider is linked; if not, the provider wins, so the account's password and sessions are removed and its email becomes verified (section 7).
  3. Otherwise a new account is made with a generated handle. It gets the email only if the provider verified it; an unverified provider email is kept on the identity but never claimed.
- One identity per provider per user. Unlinking needs another way in: a password, another provider, or a verified email (which can always get a sign-in code).
- Setup (client IDs, a domain): [deploy.md](../deploy.md#google-and-apple-sign-in).

## Deleting an account
- `DELETE /me` sets `PENDING_DELETION` with `deletion_requested_at`, ends every session, publishes `UserDeletionRequested(userId, requestedAt, eraseAfter)` and emails a notice.
- **Signing in again within 30 days cancels it** (`AccountGuard.admit`, any sign-in method; logged as `DELETION_CANCELLED`). Codes and resets still reach a pending account for that reason. `TODO(product)`: confirm the 30 days and the cancel-by-sign-in rule.
- `AccountEraser` (daily, 04:41 UTC, `app.auth.erase-cron`) erases accounts past the grace period: it keeps a tombstone row (`DELETED`, `deleted_at`, a `deleted_…` handle, display name "Deleted reader", no email or roles) so other modules' user IDs stay valid, deletes the password, linked providers, sessions, codes and sign-in events, and publishes `UserDeleted` for other modules to remove their data. The email and handle become free again.

## Proving identity for sensitive changes
Changing the password and deleting the account need the current password when the account has one, so a stolen session can't lock the owner out. Accounts without a password (code or social sign-in) need a sign-in within the last 10 minutes instead (`AccountService.RECENT_SIGN_IN`). This is the seam staff re-authentication will use.

## Tokens and cookies
- **Access token:** an ES256 JWT, 15 minutes (`app.auth.access-token-ttl`). Claims `iss`, `sub` (user), `sid` (session), `roles`, `ev` (email verified), `iat`, `exp`, and `kid` in the header (the key's RFC 7638 thumbprint). The key is `AUTH_JWT_PRIVATE_KEY` (base64 PKCS#8 P-256); without one the API makes a new key at startup and logs a warning ([deploy.md](../deploy.md#sign-in-settings)).
- **Refresh token:** 32 random bytes, stored as a SHA-256 hash, 30 days (`app.auth.refresh-token-ttl`), sliding: every refresh spends it, issues a new one and extends the session. A spent token presented again within 20 seconds is a race between tabs (`409`, the session survives); later, it is a stolen copy, so the whole session is revoked and `REFRESH_REUSED` is logged.
- **Cookies:** `access_token` (path `/`) and `refresh_token` (path `/api/v1/auth/refresh`), both `httpOnly`, `SameSite=Lax`, and `Secure` except in the `local` profile (`app.cookies.secure`).
- **Bearer header:** `Authorization: Bearer <access token>` works too (for mobile later). `TODO(identity)`: mobile clients will get the tokens in the response body.

## How requests are authenticated
`shared.internal.SecurityConfig` turns the token into the principal and checks it, so other modules only register routes:
- `AuthenticatedEndpoints` beans list routes that need a signed-in user. `PublicGetEndpoints` and `PublicPostEndpoints` list open ones; everything else is denied.
- The token comes from the bearer header, or else the access cookie. Public routes ignore the cookie, so an expired cookie never breaks a public page.
- **CSRF (double submit):** a request authenticated by cookie must echo the `XSRF-TOKEN` cookie (readable by scripts) in the `X-XSRF-TOKEN` header. Requests with an `Authorization` header skip the check. The access cookie is read only after the CSRF filter has run (`CsrfCheckedFilter`): Spring's resource server otherwise tells CSRF to skip every request its token resolver finds a token in, cookie included. Register, login and refresh skip it too: they take JSON bodies (no cross-site form can send them) and the refresh cookie is `SameSite=Lax`. Logout needs it (`PublicPostEndpoints.withCsrf`).
- **Revocation:** signing out and refresh-token reuse write the session ID to Redis (`auth:revoked:<sid>`) for one access-token lifetime, and the JWT decoder rejects tokens of those sessions. If Redis is down the check is skipped and access tokens stay valid until they expire (15 minutes at most).

## Guardrails
- **Password hashing:** Argon2id (Spring Security defaults). An unknown email still runs a hash, so timing reveals nothing.
- **Login backoff:** after 5 failed logins for one email within 15 minutes, logins for it are locked for 30 seconds, doubling with each further failure up to 15 minutes (`429` with `Retry-After`). A successful login clears it. Never permanent.
- **Rate limits** (`shared.RateLimiter`, per IP unless noted): register 10 an hour, and 5 an hour per email; login 30 per 15 minutes, and 10 per 15 minutes per email; `email/start` and `forgot` 20 an hour, and 5 an hour per email (shared); `email/verify` and `reset` 30 per 15 minutes; confirmation resends 5 an hour per user; refresh 120 a minute. Per-email limits also cap how many emails a stranger can trigger to someone's inbox, but they let a stranger use up someone's code requests for an hour (`TODO(product)`: revisit if abused).
- **Same answer either way:** register, `email/start` and `forgot` answer `202` whatever the email; emails go out on another thread, so timing doesn't tell either. Register hashes the password on both paths.
- **Bot check:** `BotChallengeVerifier` on register, `email/start` and `forgot`. `TurnstileVerifier` checks the `botToken` with Cloudflare when `TURNSTILE_SECRET_KEY` is set, and refuses the sign-up if Cloudflare can't be reached; without the secret (locally) the check is off.
- **Audit:** `auth_events` records `PASSWORD_CHANGED`, `PROFILE_CHANGED`, `DELETION_REQUESTED`, `DELETION_CANCELLED`, `REGISTERED`, `IDENTITY_LINKED`, `IDENTITY_UNLINKED`, `SIGNED_IN`, `SIGN_IN_FAILED` and `CODE_FAILED` (with the user when the email exists), `SIGNED_OUT`, `REFRESH_REUSED`, `EMAIL_VERIFIED`, `PASSWORD_RESET`, `PASSWORD_REMOVED` and `SESSIONS_REVOKED`, with IP and user agent. Failures are committed even though the request fails.
- **Retention:** `IdentityCleanup` runs daily at 04:23 UTC (`app.auth.cleanup-cron`): it deletes codes a day after they expire, expired refresh tokens, and `auth_events` older than `app.auth.event-retention` (180 days).

## Events
`UserRegistered(userId, handle, registeredAt)`, `EmailVerified(userId, verifiedAt)`, `PasswordChanged(userId, changedAt)`, `UserDeletionRequested(userId, requestedAt, eraseAfter)`, `UserDeleted(userId, deletedAt)`, and `AccountEmailRequested` for `notifications` (see above). The rest from CLAUDE.md section 7 arrive with the flows that cause them.

## Data (schema `identity`)
All tables come from the step 1 migration. Written so far: `users`, `credentials`, `user_roles` (every new user is a `READER`), `sessions`, `refresh_tokens`, `verification_codes` and `auth_events`. The `identity` module is the first to write through JPA; its entities implement `Persistable`, so app-generated UUIDv7 IDs insert without a select first.

## Web (step 2e)
- Pages: `/login` (provider buttons, then an emailed code or a password), `/signup` (same choices; every email path ends with a code), `/login/reset`, and `/account` (profile, email confirmation, password, sign-in methods, devices, deletion). All `noindex`; the account page renders in the browser.
- `web/src/lib/auth/client.ts` calls the API: CSRF header on writes, one shared refresh and a retry on `401`. `SessionProvider` holds the signed-in user for the header's account menu.
- Google and Apple buttons appear only when `GOOGLE_CLIENT_IDS` / `APPLE_CLIENT_IDS` are set for the web container (the first ID is the web client). Each attempt gets a fresh nonce.
- The CSRF cookie is never set on public reads (`SecurityConfig.SpaCsrfHandler`), so cacheable responses carry no `Set-Cookie`; the browser gets it from `/me` or a sign-in response.
- e2e (`web/e2e/auth.spec.ts`): the pages with axe in both themes; password sign-up with the emailed confirmation, profile change, sign-out and back in; code sign-up and sign-in; the wrong-password message; deletion; renewal of an expired access token.

## Open questions
- `TODO(product)`: how long to keep `auth_events` (default 180 days).
- `TODO(product)`: accounts made with a code or a provider get a random handle unless the sign-up form sends one; readers can rename it (`PATCH /me`). Handle changes are not rate limited beyond 20 profile changes an hour, and old handles are not reserved.
- `TODO(product)`: unconfirmed password accounts are kept forever, holding their email and handle. A cleanup after some weeks may be wanted.
- `TODO(product)`: registering answers the same for new and taken emails, but registering and then signing in with the chosen password still tells the two apart (it works only for a new account). Rate limits make this slow; closing it fully would mean new password accounts can't sign in until they confirm their email.
- Email codes need a domain in production: the email provider only sends from a verified domain ([deploy.md](../deploy.md#email-settings)).
- Staff roles will need TOTP and recent re-authentication (`Role`, seam only).
