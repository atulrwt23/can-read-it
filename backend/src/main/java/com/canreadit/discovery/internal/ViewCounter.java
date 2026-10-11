package com.canreadit.discovery.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Live view counters in Redis (ADR 0005): one sorted set per UTC day, {@code views:<day>}, scoring series by views.
 * A view counts once per visitor, series and day. The visitor is a fingerprint of IP and user agent, keyed with a
 * random secret that changes every day and expires: it can't be reversed or linked across days, and nothing
 * personal is stored. Counting never fails a request: if Redis is down, the view is lost.
 *
 * <p>Changing the user agent makes a new visitor, so one IP counts at most {@link #MAX_VISITORS_PER_IP} visitors per
 * series and day. That stops a script from inflating a series, at the cost of undercounting many readers behind one
 * shared address.
 */
@Component
class ViewCounter {

    private static final Logger log = LoggerFactory.getLogger(ViewCounter.class);

    /** Crawlers and link previews are not readers. */
    private static final Pattern BOTS =
            Pattern.compile("bot|crawl|spider|slurp|preview|facebookexternalhit|headless", Pattern.CASE_INSENSITIVE);

    /** Day keys outlive their day long enough for the roll-up to read them. */
    static final Duration DAY_KEY_TTL = Duration.ofDays(3);

    // TODO(product): tune once real traffic shows how many readers share an address (mobile carrier NAT).
    static final int MAX_VISITORS_PER_IP = 30;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    ViewCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String dayKey(LocalDate day) {
        return "views:" + day;
    }

    static boolean isBot(String userAgent) {
        return userAgent.isBlank() || BOTS.matcher(userAgent).find();
    }

    /**
     * Counts a view unless this visitor already viewed this series today, or this IP already brought too many
     * visitors to it. Returns whether it counted.
     */
    boolean record(UUID seriesId, String clientIp, String userAgent, LocalDate day) {
        if (isBot(userAgent)) {
            return false;
        }
        try {
            String salt = salt(day);
            String visitor = fingerprint(salt, clientIp + "\n" + userAgent);
            Boolean first = redis.opsForValue()
                    .setIfAbsent("views:seen:%s:%s:%s".formatted(day, seriesId, visitor), "1", DAY_KEY_TTL);
            if (!Boolean.TRUE.equals(first)) {
                return false;
            }
            String ipKey = "views:ip:%s:%s:%s".formatted(day, seriesId, fingerprint(salt, clientIp));
            Long visitorsFromIp = redis.opsForValue().increment(ipKey);
            redis.expire(ipKey, DAY_KEY_TTL);
            if (visitorsFromIp == null || visitorsFromIp > MAX_VISITORS_PER_IP) {
                return false;
            }
            redis.opsForZSet().incrementScore(dayKey(day), seriesId.toString(), 1);
            redis.expire(dayKey(day), DAY_KEY_TTL);
            return true;
        } catch (DataAccessException e) {
            log.debug("View not counted, Redis unavailable: {}", e.getMessage());
            return false;
        }
    }

    /** The day's secret, created by whichever instance needs it first. */
    private String salt(LocalDate day) {
        String key = "views:salt:" + day;
        byte[] fresh = new byte[32];
        random.nextBytes(fresh);
        redis.opsForValue().setIfAbsent(key, HexFormat.of().formatHex(fresh), DAY_KEY_TTL);
        String salt = redis.opsForValue().get(key);
        if (salt == null) {
            throw new IllegalStateException("Redis lost the salt for " + day);
        }
        return salt;
    }

    private static String fingerprint(String salt, String visitor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(visitor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
