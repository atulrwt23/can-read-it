package com.canreadit.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesVersion7WithRfcVariant() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheCreationMillisecond() {
        Instant now = Instant.parse("2026-09-26T12:00:00.123Z");

        UUID id = Ids.newId(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void sortsByCreationTimeAcrossMilliseconds() {
        UUID earlier = Ids.newId(Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        UUID later = Ids.newId(Clock.fixed(Instant.ofEpochMilli(1_001), ZoneOffset.UTC));

        // Postgres compares uuids as unsigned bytes; the textual form sorts the same way.
        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void doesNotRepeat() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Ids.newId());
        }

        assertThat(ids).hasSize(10_000);
    }
}
