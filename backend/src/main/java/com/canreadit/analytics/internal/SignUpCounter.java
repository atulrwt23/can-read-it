package com.canreadit.analytics.internal;

import com.canreadit.identity.UserRegistered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Counts sign-ups from identity's event; who signed up is not recorded. */
@Component
class SignUpCounter {

    private final AnalyticsService analytics;

    SignUpCounter(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @ApplicationModuleListener
    void on(UserRegistered event) {
        analytics.recordServerEvent(EventName.SIGN_UP, event.registeredAt());
    }
}
