package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import org.springframework.http.HttpStatus;

/** Errors shared by the sign-in flows. Their codes are part of the API. */
final class AuthErrors {

    private AuthErrors() {}

    static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "The email or password is wrong.");
    }

    static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again.");
    }

    /** Wrong, expired, already used or out of attempts: one answer, so a guesser learns nothing. */
    static ApiException invalidCode() {
        return ApiException.badRequest("invalid_code", "The code is wrong or has expired. Ask for a new one.");
    }

    /** Only said once the caller has proved they own the account (right password or code). */
    static ApiException accountUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "account_unavailable", "This account can't sign in.");
    }

    static ApiException handleTaken() {
        return new ApiException(HttpStatus.CONFLICT, "handle_taken", "This handle is taken.");
    }

    static ApiException botCheckFailed() {
        return ApiException.badRequest("bot_check_failed", "The bot check failed. Reload the page and try again.");
    }
}
