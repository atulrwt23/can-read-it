package com.canreadit.shared;

/** Answered with {@code 301 Moved Permanently} and the given {@code Location}. */
public class MovedPermanentlyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String location;

    public MovedPermanentlyException(String location) {
        super("Moved permanently to " + location);
        this.location = location;
    }

    public String location() {
        return location;
    }
}
