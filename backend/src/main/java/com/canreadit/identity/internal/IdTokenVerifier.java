package com.canreadit.identity.internal;

import org.jspecify.annotations.Nullable;

/**
 * Checks an OpenID Connect ID token that a client got from a provider's SDK (CLAUDE.md section 7): signature against
 * the provider's published keys, issuer, audience, expiry and nonce.
 */
interface IdTokenVerifier {

    /** Whether the provider is configured (has at least one client ID). */
    boolean enabled(IdentityProvider provider);

    /**
     * @param expectedNonce the nonce we issued for this sign-in; the token must carry it, or its SHA-256 (hex), as
     *     some SDKs send the hash to the provider
     * @throws com.canreadit.shared.ApiException {@code 401 invalid_id_token} for any token we don't accept
     */
    VerifiedIdToken verify(IdentityProvider provider, String idToken, String expectedNonce);

    /** The claims we use from a verified token. */
    record VerifiedIdToken(String subject, @Nullable String email, boolean emailVerified) {}
}
