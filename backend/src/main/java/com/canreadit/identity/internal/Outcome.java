package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The result of a transaction that must commit even when the request fails: a reused refresh token revokes its
 * session, a wrong code counts an attempt. Throwing inside would roll that back, so the failure is returned and
 * thrown after the commit with {@link #get()}.
 */
record Outcome<T>(@Nullable T value, @Nullable ApiException error) {

    static <T> Outcome<T> ok(T value) {
        return new Outcome<>(value, null);
    }

    static <T> Outcome<T> failed(ApiException error) {
        return new Outcome<>(null, error);
    }

    T get() {
        if (error != null) {
            throw error;
        }
        return Objects.requireNonNull(value);
    }
}
