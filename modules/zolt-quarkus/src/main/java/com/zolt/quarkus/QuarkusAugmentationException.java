package com.zolt.quarkus;

public final class QuarkusAugmentationException extends RuntimeException {
    public QuarkusAugmentationException(String message) {
        super(message);
    }

    public QuarkusAugmentationException(String message, Throwable cause) {
        super(message, cause);
    }
}
