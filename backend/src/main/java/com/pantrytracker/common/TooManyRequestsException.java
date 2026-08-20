package com.pantrytracker.common;

/**
 * Returned as HTTP 429 when a client exceeds an authentication rate limit.
 * The message is intentionally generic — it never reveals whether an email
 * or account exists.
 */
public class TooManyRequestsException extends RuntimeException {

    public static final String MESSAGE = "Too many authentication attempts. Please try again later.";

    public TooManyRequestsException() {
        super(MESSAGE);
    }
}