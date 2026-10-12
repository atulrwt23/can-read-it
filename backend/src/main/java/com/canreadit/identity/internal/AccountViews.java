package com.canreadit.identity.internal;

import java.util.Locale;
import org.springframework.stereotype.Component;

/** Builds {@link MeResponse}, which needs the user's sign-in methods as well as the user. */
@Component
class AccountViews {

    private final Credentials credentials;
    private final UserIdentities identities;

    AccountViews(Credentials credentials, UserIdentities identities) {
        this.credentials = credentials;
        this.identities = identities;
    }

    MeResponse of(UserAccount user) {
        return new MeResponse(
                user.getId(),
                user.handle(),
                user.displayName(),
                user.email(),
                user.emailVerified(),
                user.roleNames(),
                credentials.existsById(user.getId()),
                identities.findByUserId(user.getId()).stream()
                        .map(identity -> identity.provider().name().toLowerCase(Locale.ROOT))
                        .sorted()
                        .toList(),
                user.createdAt());
    }
}
