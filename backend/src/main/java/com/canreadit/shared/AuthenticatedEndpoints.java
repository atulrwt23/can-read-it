package com.canreadit.shared;

import java.util.List;

/**
 * Ant-style path patterns a module serves to signed-in users, for any method. Modules register one of these as a
 * bean; everything not listed stays denied by default.
 */
public record AuthenticatedEndpoints(List<String> patterns) {

    public static AuthenticatedEndpoints of(String... patterns) {
        return new AuthenticatedEndpoints(List.of(patterns));
    }
}
