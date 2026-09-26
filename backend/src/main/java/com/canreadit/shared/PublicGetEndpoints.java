package com.canreadit.shared;

import java.util.List;

/**
 * Ant-style path patterns a module serves anonymously for {@code GET}. Modules register one of
 * these as a bean; everything not listed stays denied by default.
 */
public record PublicGetEndpoints(List<String> patterns) {

    public static PublicGetEndpoints of(String... patterns) {
        return new PublicGetEndpoints(List.of(patterns));
    }
}
