package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * One-time nonces for social sign-in. The client gets one from us, passes it to the provider's SDK, and sends it back
 * with the ID token; the token must carry it. Because we issue each nonce and accept it once, a captured ID token
 * can't be replayed. Unlike rate limits, this fails closed: without Redis, social sign-in waits.
 */
@Component
class OAuthNonces {

    static final Duration LIFETIME = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    OAuthNonces(StringRedisTemplate redis) {
        this.redis = redis;
    }

    String issue() {
        String nonce = OpaqueTokens.generate();
        try {
            redis.opsForValue().set(key(nonce), "1", LIFETIME);
        } catch (DataAccessException e) {
            throw unavailable();
        }
        return nonce;
    }

    /** Spends the nonce. False if we never issued it, it expired, or it was already used. */
    boolean consume(String nonce) {
        try {
            return redis.opsForValue().getAndDelete(key(nonce)) != null;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private static String key(String nonce) {
        return "auth:oauth-nonce:" + OpaqueTokens.hash(nonce);
    }

    private static ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "try_again", "Sign-in is briefly unavailable. Try again.");
    }
}
