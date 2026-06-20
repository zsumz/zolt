package com.zolt.maven;

import java.time.Duration;
import java.util.Objects;

public record RepositoryHttpPolicy(
        Duration requestTimeout,
        int maxAttempts,
        Duration retryBackoff) {
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(100);

    public RepositoryHttpPolicy {
        requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("Repository request timeout must be greater than zero.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Repository max attempts must be at least 1.");
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("Repository retry backoff must not be negative.");
        }
    }

    public static RepositoryHttpPolicy defaults() {
        return new RepositoryHttpPolicy(DEFAULT_REQUEST_TIMEOUT, DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_BACKOFF);
    }
}
