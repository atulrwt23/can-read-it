package com.canreadit.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** Generates time-ordered UUIDv7 identifiers (RFC 9562), sortable by creation millisecond. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        return newId(Clock.systemUTC());
    }

    static UUID newId(Clock clock) {
        long millis = clock.millis();
        long randA = RANDOM.nextInt(1 << 12);
        long randB = RANDOM.nextLong();
        // 48-bit Unix millis | 4-bit version (7) | 12 random bits
        long msb = (millis << 16) | 0x7000L | randA;
        // 2-bit variant (10) | 62 random bits
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
