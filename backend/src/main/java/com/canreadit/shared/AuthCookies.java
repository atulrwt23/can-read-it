package com.canreadit.shared;

/**
 * Names of the cookies that carry a browser's session (CLAUDE.md section 7). Identity sets them; the security
 * configuration reads the access token from them.
 */
public final class AuthCookies {

    /** The short-lived JWT, sent with every same-origin request. */
    public static final String ACCESS = "access_token";

    /** The opaque refresh token, sent only to the refresh endpoint. */
    public static final String REFRESH = "refresh_token";

    public static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private AuthCookies() {}
}
