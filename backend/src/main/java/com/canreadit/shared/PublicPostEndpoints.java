package com.canreadit.shared;

import java.util.List;

/**
 * Ant-style path patterns a module accepts anonymous {@code POST}s on, such as fire-and-forget beacons. They carry
 * no session or cookie, so CSRF protection does not apply to them. Everything not listed stays denied by default.
 */
public record PublicPostEndpoints(List<String> patterns) {

    public static PublicPostEndpoints of(String... patterns) {
        return new PublicPostEndpoints(List.of(patterns));
    }
}
