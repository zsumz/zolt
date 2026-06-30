package com.zolt.error;

import java.util.Optional;

/**
 * Immutable carrier for a user-facing error that always explains what to do next.
 *
 * <p>{@code summary} states what failed and {@code remediation} is the explicit "what to do next"
 * line. Both are required and validated non-blank so the CLI renderer never has to guess a
 * remediation line. The optional {@code cause} preserves the originating throwable for logging.
 */
public record ActionableError(String summary, String remediation, Optional<Throwable> cause) {
    public ActionableError {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("ActionableError summary must not be blank.");
        }
        if (remediation == null || remediation.isBlank()) {
            throw new IllegalArgumentException(
                    "ActionableError remediation must not be blank; every error must explain what to do next.");
        }
        summary = summary.trim();
        remediation = remediation.trim();
        cause = cause == null ? Optional.empty() : cause;
    }

    public static ActionableError of(String summary, String remediation) {
        return new ActionableError(summary, remediation, Optional.empty());
    }

    public static ActionableError of(String summary, String remediation, Throwable cause) {
        return new ActionableError(summary, remediation, Optional.ofNullable(cause));
    }

    /**
     * Flat message for legacy string consumers: {@code summary + ' ' + remediation}. Keeps
     * existing {@code contains}-style assertions and flat-string error paths working unchanged.
     */
    public String message() {
        return summary + " " + remediation;
    }
}
