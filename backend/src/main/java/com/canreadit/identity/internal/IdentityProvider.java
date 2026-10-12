package com.canreadit.identity.internal;

import java.util.Locale;
import java.util.Optional;

/** Social sign-in providers ({@code user_identities.provider}). */
enum IdentityProvider {
    GOOGLE,
    APPLE;

    /** From a URL path segment such as {@code google}. */
    static Optional<IdentityProvider> fromPath(String value) {
        for (IdentityProvider provider : values()) {
            if (provider.name().equals(value.toUpperCase(Locale.ROOT))) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
