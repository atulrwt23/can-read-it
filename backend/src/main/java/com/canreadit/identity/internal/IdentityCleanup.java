package com.canreadit.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily retention (CLAUDE.md section 7): deletes verification codes a day after they expire, refresh tokens once
 * they expire, and sign-in events older than {@code app.auth.event-retention}.
 */
@Component
class IdentityCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdentityCleanup.class);

    private final JdbcClient jdbc;
    private final AuthProperties properties;
    private final Clock clock;

    IdentityCleanup(JdbcClient jdbc, AuthProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.auth.cleanup-cron:0 23 4 * * *}", zone = "UTC")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        int codes = delete(
                "delete from identity.verification_codes where expires_at < :cutoff", now.minus(Duration.ofDays(1)));
        int tokens = delete("delete from identity.refresh_tokens where expires_at < :cutoff", now);
        int events = delete(
                "delete from identity.auth_events where created_at < :cutoff", now.minus(properties.eventRetention()));
        log.info("Identity cleanup removed {} codes, {} refresh tokens and {} sign-in events", codes, tokens, events);
    }

    private int delete(String sql, Instant cutoff) {
        return jdbc.sql(sql)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }
}
