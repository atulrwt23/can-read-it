package com.canreadit.identity.internal;

import com.canreadit.identity.RoleGranted;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bootstraps staff: a user whose <em>verified</em> email is in {@code app.auth.admin-emails} ({@code ADMIN_EMAILS})
 * gets {@link Role#ADMIN} when they sign in. An unverified email never qualifies, or anyone could register an admin's
 * address. Removing an email from the list does not take the role away. TODO(product): manage staff roles from an
 * admin screen (with RoleRevoked), and require TOTP for staff (section 7).
 */
@Component
class StaffRoles {

    private final Set<String> adminEmails;
    private final AuthEventLog events;
    private final ApplicationEventPublisher publisher;

    StaffRoles(AuthProperties properties, AuthEventLog events, ApplicationEventPublisher publisher) {
        this.adminEmails = properties.adminEmails() == null
                ? Set.of()
                : properties.adminEmails().stream()
                        .map(email -> email.strip().toLowerCase(Locale.ROOT))
                        .filter(email -> !email.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        this.events = events;
        this.publisher = publisher;
    }

    /** Grants ADMIN if the user qualifies and lacks it. Called inside the sign-in transaction, before tokens. */
    void apply(UserAccount user, ClientInfo client, Instant now) {
        String email = user.email();
        if (email == null || !user.emailVerified() || !adminEmails.contains(email.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (user.grant(Role.ADMIN, now)) {
            events.record(AuthEventLog.Type.ROLE_GRANTED, user.getId(), client);
            publisher.publishEvent(new RoleGranted(user.getId(), Role.ADMIN.name(), null, now));
        }
    }
}
