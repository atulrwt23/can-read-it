package com.canreadit.notifications.internal;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param siteUrl the public site, for links in emails; without it digests wait in the queue
 * @param digestInterval how often the digest job runs
 * @param minGap the least time between two digests to one reader
 * @param pendingTtl queued chapters older than this are dropped rather than sent late
 */
@ConfigurationProperties("app.notifications")
record NotificationsProperties(
        @Nullable String siteUrl, Duration digestInterval, Duration minGap, Duration pendingTtl) {

    /** The site URL without a trailing slash, or null when it isn't set. */
    @Nullable
    String site() {
        if (siteUrl == null || siteUrl.isBlank()) {
            return null;
        }
        return siteUrl.strip().replaceAll("/+$", "");
    }
}
