package com.canreadit.shared;

import org.springframework.http.HttpStatus;

/**
 * An error that maps to an RFC 9457 Problem Details response. {@code code} is a stable,
 * machine-readable identifier (snake_case) that clients may switch on.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException gone(String code, String detail) {
        return new ApiException(HttpStatus.GONE, code, detail);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
