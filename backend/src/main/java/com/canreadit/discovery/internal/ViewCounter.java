package com.canreadit.discovery.internal;

import com.canreadit.shared.Visitors;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Live view counters in Redis (ADR 0005): one sorted set per UTC day, {@code views:<day>}, scoring series by views.
 * A view counts once per visitor, series and day. The visitor is a daily fingerprint of IP and user agent
 * ({@link Visitors}). Counting never fails a request: if Redis is down, the view is lost.
 *
 * <p>Changing the user agent makes a new visitor, so one IP counts at most {@link #MAX_VISITORS_PER_IP} visitors per
 * series and day. That stops a script from inflating a series, at the cost of undercounting many readers behind one
 * shared address.
 */
@Component
class ViewCounter {

    private static final Logger log = LoggerFactory.getLogger(ViewCounter.class);

    /** Day keys outlive their day long enough for the roll-up to read them. */
    static final Duration DAY_KEY_TTL = Duration.ofDays(3);

    // TODO(product): tune once real traffic shows how many readers share an address (mobile carrier NAT).
    static final int MAX_VISITORS_PER_IP = 30;

    private final StringRedisTemplate redis;
    private final Visitors visitors;

    ViewCounter(StringRedisTemplate redis, Visitors visitors) {
        this.redis = redis;
        this.visitors = visitors;
    }

    static String dayKey(LocalDate day) {
        return "views:" + day;
    }

    /**
     * Counts a view unless this visitor already viewed this series today, or this IP already brought too many
     * visitors to it. Returns whether it counted.
     */
    boolean record(UUID seriesId, String clientIp, String userAgent, LocalDate day) {
        if (Visitors.isBot(userAgent)) {
            return false;
        }
        var visitor = visitors.fingerprint(day, clientIp + "\n" + userAgent);
        var address = visitors.fingerprint(day, clientIp);
        if (visitor.isEmpty() || address.isEmpty()) {
            return false;
        }
        try {
            Boolean first = redis.opsForValue()
                    .setIfAbsent("views:seen:%s:%s:%s".formatted(day, seriesId, visitor.get()), "1", DAY_KEY_TTL);
            if (!Boolean.TRUE.equals(first)) {
                return false;
            }
            String ipKey = "views:ip:%s:%s:%s".formatted(day, seriesId, address.get());
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
}
