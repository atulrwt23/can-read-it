package com.canreadit.shared;

import org.springframework.http.HttpStatus;

/** A 429 Problem Details response with a {@code Retry-After} header. */
public class TooManyRequestsException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many requests. Try again later.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
