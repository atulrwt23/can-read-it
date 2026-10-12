package com.canreadit.identity.internal;

import com.canreadit.shared.Ids;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * One-time email codes ({@code identity.verification_codes}, CLAUDE.md section 7): 6 digits, valid for
 * {@link #LIFETIME}, at most {@link #MAX_ATTEMPTS} guesses, stored hashed. Issuing a code retires the earlier
 * unused codes of its group, so only the newest one works. Callers run these inside their transaction.
 *
 * <p>A 6-digit hash is easy to reverse offline, so the hash only keeps codes out of plain sight; what protects a
 * code is its short life and the attempt limit.
 */
@Component
class VerificationCodes {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    enum Purpose {
        SIGN_IN,
        VERIFY_EMAIL,
        PASSWORD_RESET,
        EMAIL_CHANGE;

        /** Codes entered in the same place: a new one replaces the others. */
        List<Purpose> group() {
            return switch (this) {
                case SIGN_IN, VERIFY_EMAIL -> List.of(SIGN_IN, VERIFY_EMAIL);
                case PASSWORD_RESET -> List.of(PASSWORD_RESET);
                case EMAIL_CHANGE -> List.of(EMAIL_CHANGE);
            };
        }
    }

    private final JdbcClient jdbc;

    VerificationCodes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A new code for the email; earlier unused codes of the same group stop working. Returns the plain code. */
    String issue(String email, Purpose purpose, Instant now) {
        retire(email, purpose.group(), now);
        UUID id = Ids.newId();
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        jdbc.sql("""
                        insert into identity.verification_codes (id, email, purpose, code_hash, expires_at)
                        values (:id, cast(:email as citext), :purpose, :hash, :expires)
                        """)
                .param("id", id)
                .param("email", email)
                .param("purpose", purpose.name())
                .param("hash", hash(id, code))
                .param("expires", utc(now.plus(LIFETIME)))
                .update();
        return code;
    }

    /**
     * Checks a code against the newest live code of the given purposes and spends it if it matches. A wrong guess
     * counts as an attempt, so the caller must commit even when the answer is empty.
     *
     * @return the purpose of the matched code, or empty for a wrong, used, expired or exhausted code
     */
    Optional<Purpose> consume(String email, List<Purpose> purposes, String code, Instant now) {
        var live = jdbc.sql("""
                        select id, purpose, code_hash from identity.verification_codes
                        where email = cast(:email as citext) and purpose in (:purposes)
                          and used_at is null and expires_at > :now and attempts < :max
                        order by id desc
                        limit 1
                        for update
                        """)
                .param("email", email)
                .param("purposes", purposes.stream().map(Purpose::name).toList())
                .param("now", utc(now))
                .param("max", MAX_ATTEMPTS)
                .query((rs, row) -> new Live(
                        rs.getObject("id", UUID.class),
                        Purpose.valueOf(rs.getString("purpose")),
                        rs.getString("code_hash")))
                .optional();
        if (live.isEmpty()) {
            return Optional.empty();
        }
        Live candidate = live.get();
        boolean matches = MessageDigest.isEqual(
                candidate.hash().getBytes(StandardCharsets.US_ASCII),
                hash(candidate.id(), code).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            jdbc.sql("update identity.verification_codes set attempts = attempts + 1 where id = :id")
                    .param("id", candidate.id())
                    .update();
            return Optional.empty();
        }
        jdbc.sql("update identity.verification_codes set used_at = :now where id = :id")
                .param("id", candidate.id())
                .param("now", utc(now))
                .update();
        return Optional.of(candidate.purpose());
    }

    private void retire(String email, List<Purpose> purposes, Instant now) {
        jdbc.sql("""
                        update identity.verification_codes set expires_at = :now
                        where email = cast(:email as citext) and purpose in (:purposes)
                          and used_at is null and expires_at > :now
                        """)
                .param("email", email)
                .param("purposes", purposes.stream().map(Purpose::name).toList())
                .param("now", utc(now))
                .update();
    }

    /** The code is salted with its row ID, so equal codes never share a hash. */
    private static String hash(UUID id, String code) {
        return OpaqueTokens.hash(id + ":" + code);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Live(UUID id, Purpose purpose, String hash) {}
}
