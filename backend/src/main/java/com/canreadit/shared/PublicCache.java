package com.canreadit.shared;

import java.time.Duration;
import org.springframework.http.CacheControl;

/**
 * Cache-Control for public, visitor-independent responses: browsers revalidate, while the CDN
 * keeps a copy for {@code sharedMaxAge} and may serve it stale while it refreshes.
 */
public final class PublicCache {

    private PublicCache() {}

    public static CacheControl sharedFor(Duration sharedMaxAge) {
        return CacheControl.maxAge(Duration.ZERO)
                .cachePublic()
                .sMaxAge(sharedMaxAge)
                .staleWhileRevalidate(sharedMaxAge.multipliedBy(5));
    }
}
