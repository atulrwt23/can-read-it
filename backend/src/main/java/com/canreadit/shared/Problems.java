package com.canreadit.shared;

import java.net.URI;

/** Stable Problem Details {@code type} URIs. The URN form keeps them independent of our domain. */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:canreadit:problem:";

    private Problems() {}

    public static URI type(String code) {
        return URI.create(TYPE_PREFIX + code);
    }
}
