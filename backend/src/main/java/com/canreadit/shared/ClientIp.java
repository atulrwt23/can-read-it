package com.canreadit.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address of the browser behind a request. Browser requests reach the API through Cloudflare, which sets
 * {@code CF-Connecting-IP} (and through the web app's proxy in quick-tunnel mode, which keeps that header and adds
 * {@code X-Forwarded-For}). Only use the result for counting and rate limiting, never for access control: the
 * headers are trusted as given.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare.strip();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].strip();
        }
        return request.getRemoteAddr();
    }

    /** Whether the request came from a browser through the edge, rather than from the web app's server side. */
    public static boolean isForwarded(HttpServletRequest request) {
        return request.getHeader("CF-Connecting-IP") != null || request.getHeader("X-Forwarded-For") != null;
    }
}
