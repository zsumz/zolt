package com.zolt.explain;

public final class MigrationExplainException extends RuntimeException {
    public MigrationExplainException(String message) {
        super(message);
    }

    public MigrationExplainException(String message, Throwable cause) {
        super(message, cause);
    }
}
