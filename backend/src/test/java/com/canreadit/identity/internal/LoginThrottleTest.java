package com.canreadit.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    @Test
    void locksDoubleFromTheFifthFailureUpToTheCap() {
        assertThat(LoginThrottle.lockFor(5)).isEqualTo(Duration.ofSeconds(30));
        assertThat(LoginThrottle.lockFor(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(LoginThrottle.lockFor(9)).isEqualTo(Duration.ofMinutes(8));
        assertThat(LoginThrottle.lockFor(10)).isEqualTo(Duration.ofMinutes(15));
        assertThat(LoginThrottle.lockFor(1_000)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void treatsEmailsCaseInsensitively() {
        assertThat(LoginThrottle.normalize("  Ana@Example.COM ")).isEqualTo("ana@example.com");
    }
}
