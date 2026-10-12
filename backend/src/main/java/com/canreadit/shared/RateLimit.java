package com.canreadit.shared;

import java.time.Duration;

/**
 * A named limit: at most {@code capacity} requests per {@code period} for one key (a client IP, an email), refilled
 * gradually. The name keeps keys of different limits apart, so it must be unique and stable.
 */
public record RateLimit(String name, long capacity, Duration period) {

    public RateLimit {
        if (!name.matches("[a-z0-9-]+") || capacity < 1 || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("Invalid rate limit " + name);
        }
    }
}
