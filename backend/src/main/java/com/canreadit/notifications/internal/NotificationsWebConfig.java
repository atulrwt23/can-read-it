package com.canreadit.notifications.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class NotificationsWebConfig {

    /** Unsubscribe links work without signing in; the token is the credential and no cookie is read. */
    @Bean
    PublicPostEndpoints notificationsUnsubscribe() {
        return PublicPostEndpoints.of("/api/v1/notifications/unsubscribe");
    }

    @Bean
    AuthenticatedEndpoints notificationSettings() {
        return AuthenticatedEndpoints.of("/api/v1/me/notifications");
    }
}
