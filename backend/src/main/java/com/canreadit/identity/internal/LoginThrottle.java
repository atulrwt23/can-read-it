package com.canreadit.identity.internal;

import com.canreadit.shared.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing on one account (CLAUDE.md section 7): after {@value #FREE_FAILURES} failed logins
 * within {@link #WINDOW}, each further failure locks logins for twice as long as the last, from
 * {@link #FIRST_LOCK} up to {@link #MAX_LOCK}. Locks never become permanent, and a successful login clears them.
 * The key is the email as typed, so unknown emails behave like real ones. Fails open if Redis is down; the
 * per-IP and per-email rate limits still apply.
 */
@Component
class LoginThrottle {

    static final int FREE_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration FIRST_LOCK = Duration.ofSeconds(30);
    static final Duration MAX_LOCK = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    LoginThrottle(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @throws TooManyRequestsException while the account is locked */
    void check(String email) {
        try {
            Long seconds = redis.getExpire(lockKey(email));
            if (seconds != null && seconds > 0) {
                throw new TooManyRequestsException(seconds);
            }
        } catch (DataAccessException e) {
            // Fail open.
        }
    }

    void failed(String email) {
        try {
            Long failures = redis.opsForValue().increment(failuresKey(email));
            if (failures == null) {
                return;
            }
            if (failures == 1) {
                redis.expire(failuresKey(email), WINDOW);
            }
            if (failures >= FREE_FAILURES) {
                redis.opsForValue().set(lockKey(email), "1", lockFor(failures));
            }
        } catch (DataAccessException e) {
            // Fail open.
        }
    }

    void succeeded(String email) {
        try {
            redis.delete(List.of(failuresKey(email), lockKey(email)));
        } catch (DataAccessException e) {
            // Fail open.
        }
    }

    static Duration lockFor(long failures) {
        long doublings = Math.min(failures - FREE_FAILURES, 10);
        Duration lock = FIRST_LOCK.multipliedBy(1L << Math.max(0, doublings));
        return lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock;
    }

    private static String failuresKey(String email) {
        return "auth:login-failures:" + OpaqueTokens.hash(normalize(email));
    }

    private static String lockKey(String email) {
        return "auth:login-lock:" + OpaqueTokens.hash(normalize(email));
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
