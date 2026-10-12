package com.canreadit.shared;

import java.util.List;

/**
 * Ant-style path patterns a module accepts anonymous {@code POST}s on. Everything not listed stays denied by default.
 *
 * <p>{@link #of} is for requests that rely on no cookie, such as fire-and-forget beacons or sign-in itself: CSRF
 * protection does not apply to them. {@link #withCsrf} keeps CSRF protection for anonymous requests that act on the
 * browser's cookies, such as signing out.
 *
 * @param csrfProtected whether these requests must carry the CSRF token
 */
public record PublicPostEndpoints(List<String> patterns, boolean csrfProtected) {

    public static PublicPostEndpoints of(String... patterns) {
        return new PublicPostEndpoints(List.of(patterns), false);
    }

    public static PublicPostEndpoints withCsrf(String... patterns) {
        return new PublicPostEndpoints(List.of(patterns), true);
    }
}
