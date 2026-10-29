package com.docuinsight.docuinsight.service;

// Thrown when a user has used up today's allowance (Day 17). Mapped to HTTP 429.
public class QuotaExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public QuotaExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}