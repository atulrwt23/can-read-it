package com.canreadit.shared.internal;

import com.canreadit.shared.Visitors;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Daily visitor keys in Redis, shared by every API instance. The first instance that needs a day's key creates it;
 * it expires a few days later, after which that day's fingerprints can't be recomputed.
 */
@Component
class RedisVisitors implements Visitors {

    private static final Logger log = LoggerFactory.getLogger(RedisVisitors.class);

    /** Kept a little past its day, so late counting jobs still find it. The key name predates this class. */
    static final Duration KEY_TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    RedisVisitors(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> fingerprint(LocalDate day, String details) {
        try {
            return Optional.of(hmac(key(day), details));
        } catch (DataAccessException e) {
            log.debug("No visitor key, Redis unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String key(LocalDate day) {
        String name = "views:salt:" + day;
        byte[] fresh = new byte[32];
        random.nextBytes(fresh);
        redis.opsForValue().setIfAbsent(name, HexFormat.of().formatHex(fresh), KEY_TTL);
        String key = redis.opsForValue().get(name);
        if (key == null) {
            throw new IllegalStateException("Redis lost the visitor key for " + day);
        }
        return key;
    }

    private static String hmac(String key, String details) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(details.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
