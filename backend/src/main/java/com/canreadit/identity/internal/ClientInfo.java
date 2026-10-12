package com.canreadit.identity.internal;

import com.canreadit.shared.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

/**
 * Who is signing in, for sessions and the audit log. The IP comes from forwarding headers, so it is recorded, never
 * trusted for access decisions. An address that doesn't parse is dropped rather than looked up.
 */
record ClientInfo(
        String ipText, @Nullable InetAddress ip, @Nullable String userAgent) {

    private static final int MAX_USER_AGENT = 512;

    static ClientInfo from(HttpServletRequest request) {
        String ipText = ClientIp.of(request);
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        return new ClientInfo(
                ipText,
                parse(ipText),
                agent == null ? null : agent.substring(0, Math.min(agent.length(), MAX_USER_AGENT)));
    }

    /** A literal IPv4 or IPv6 address, never a DNS lookup. */
    static @Nullable InetAddress parse(String text) {
        try {
            return InetAddress.ofLiteral(text.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
