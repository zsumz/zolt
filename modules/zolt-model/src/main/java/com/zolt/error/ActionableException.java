package com.zolt.error;

/**
 * Runtime exception that carries a typed {@link ActionableError} through the CLI's existing
 * execution-exception plumbing.
 *
 * <p>{@link #getMessage()} returns {@code summary + ' ' + remediation} so legacy string consumers
 * and {@code contains}-style assertions keep working, while {@link #error()} exposes the structured
 * carrier so the renderer can render the summary and remediation directly instead of guessing.
 */
public final class ActionableException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public ActionableException(ActionableError error) {
        super(message(error), error == null ? null : error.cause().orElse(null));
        if (error == null) {
            throw new IllegalArgumentException("ActionableException requires a non-null ActionableError.");
        }
        this.error = error;
    }

    public ActionableException(String summary, String remediation) {
        this(ActionableError.of(summary, remediation));
    }

    public ActionableError error() {
        return error;
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }

    private static String message(ActionableError error) {
        return error == null ? null : error.message();
    }
}
