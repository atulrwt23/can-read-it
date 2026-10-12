package com.canreadit.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.canreadit.shared.RateLimit;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisRateLimiterTest {

    static final RateLimit LIMIT = new RateLimit("login", 5, Duration.ofMinutes(15));

    @Test
    void bucketKeysNeverContainTheClientKey() {
        String key = RedisRateLimiter.bucketKey(LIMIT, "reader@example.com");

        assertThat(key)
                .startsWith("ratelimit:login:")
                .doesNotContain("reader")
                .hasSize("ratelimit:login:".length() + 32);
        assertThat(RedisRateLimiter.bucketKey(LIMIT, "reader@example.com")).isEqualTo(key);
        assertThat(RedisRateLimiter.bucketKey(LIMIT, "other@example.com")).isNotEqualTo(key);
    }

    @Test
    void rejectsLimitsThatCouldCollideOrNeverRefill() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RateLimit("has:colon", 5, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RateLimit("zero", 0, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RateLimit("instant", 5, Duration.ZERO));
    }
}
