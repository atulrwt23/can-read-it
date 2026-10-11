package com.canreadit.shared;

/**
 * Per-client rate limits shared by every API instance. Modules call it for endpoints that are expensive or easy to
 * abuse. If the store is unavailable the request is allowed: a rate limit must never take the site down.
 */
public interface RateLimiter {

    /**
     * Takes one request from the bucket for this limit and key.
     *
     * @throws TooManyRequestsException when the bucket is empty
     */
    void check(RateLimit limit, String key);
}
