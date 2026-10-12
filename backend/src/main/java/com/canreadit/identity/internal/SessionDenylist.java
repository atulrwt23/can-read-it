package com.canreadit.identity.internal;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Sessions ended before their access tokens expire (sign-out, a reused refresh token, later bans and password
 * changes). Entries live in Redis as long as an access token can, so the list stays small. If Redis is down,
 * revoked access tokens keep working until they expire (at most the access-token lifetime): refresh still fails,
 * because the session is revoked in Postgres.
 */
@Component
class SessionDenylist {

    private static final Logger log = LoggerFactory.getLogger(SessionDenylist.class);
    private static final long WARN_EVERY_MS = 60_000;

    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final AtomicLong lastWarning = new AtomicLong();

    SessionDenylist(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    static String key(UUID sessionId) {
        return "auth:revoked:" + sessionId;
    }

    void revoke(UUID sessionId) {
        try {
            redis.opsForValue().set(key(sessionId), "1", properties.accessTokenTtl());
        } catch (DataAccessException e) {
            warn(e);
        }
    }

    boolean isRevoked(UUID sessionId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
        } catch (DataAccessException e) {
            warn(e);
            return false;
        }
    }

    private void warn(DataAccessException e) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last >= WARN_EVERY_MS && lastWarning.compareAndSet(last, now)) {
            log.warn("Session denylist unavailable, Redis error: {}", e.toString());
        }
    }
}
