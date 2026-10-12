package com.canreadit.identity.internal;

import com.canreadit.identity.UserDeleted;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erases accounts whose deletion grace period is over (daily). Identity keeps a tombstone row so other modules' user
 * IDs stay valid, and drops everything personal: email, handle, display name, password, linked providers, sessions,
 * codes and sign-in events. {@link UserDeleted} tells the other modules to do the same with their data.
 */
@Component
class AccountEraser {

    private static final Logger log = LoggerFactory.getLogger(AccountEraser.class);

    private final UserAccounts users;
    private final AccountGuard guard;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    AccountEraser(
            UserAccounts users,
            AccountGuard guard,
            JdbcClient jdbc,
            ApplicationEventPublisher publisher,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.guard = guard;
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.auth.erase-cron:0 41 4 * * *}", zone = "UTC")
    public void run() {
        Instant cutoff = clock.instant().minus(AccountService.DELETION_GRACE);
        List<UUID> due = users.findDueForErasure(cutoff);
        for (UUID id : due) {
            // One transaction per account, so one failure doesn't block the rest.
            tx.executeWithoutResult(status -> erase(id, cutoff));
        }
        if (!due.isEmpty()) {
            log.info("Erased {} accounts after their deletion grace period", due.size());
        }
    }

    private void erase(UUID id, Instant cutoff) {
        UserAccount user = users.findById(id).orElse(null);
        Instant requested = user == null ? null : user.deletionRequestedAt();
        if (user == null
                || user.status() != UserStatus.PENDING_DELETION
                || requested == null
                || requested.isAfter(cutoff)) {
            // Signed in again since the list was read.
            return;
        }
        Instant now = clock.instant();
        String email = user.email();
        for (String table : List.of("credentials", "user_identities", "sessions", "auth_events")) {
            jdbc.sql("delete from identity." + table + " where user_id = :id")
                    .param("id", id)
                    .update();
        }
        if (email != null) {
            jdbc.sql("delete from identity.verification_codes where email = cast(:email as citext)")
                    .param("email", email)
                    .update();
        }
        user.erase(guard.randomHandle("deleted_"), now);
        publisher.publishEvent(new UserDeleted(id, now));
    }
}
