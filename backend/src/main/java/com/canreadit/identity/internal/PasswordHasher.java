package com.canreadit.identity.internal;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hashes (Spring Security's recommended parameters). When an email has no password, the given
 * password is still checked against a fixed hash, so the response takes as long as for a real account and timing
 * doesn't reveal which emails are registered.
 */
@Component
class PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String decoy = encoder.encode("decoy-password-for-constant-time");

    String hash(String password) {
        return encoder.encode(password);
    }

    boolean matches(String password, String hash) {
        return encoder.matches(password, hash);
    }

    /** Spends the same time as {@link #matches} and always fails. */
    boolean matchesNothing(String password) {
        encoder.matches(password, decoy);
        return false;
    }
}
