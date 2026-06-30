package com.zolt.error;

/**
 * Implemented by exceptions that carry a typed {@link ActionableError} so the CLI renderer can
 * render the structured summary and remediation directly instead of guessing the remediation line.
 *
 * <p>{@link ActionableException} is the canonical carrier for new errors. Existing exception types
 * (for example domain config exceptions) may also implement this so high-traffic sites can be
 * migrated to a structured remediation without changing the exception type their callers catch.
 */
public interface HasActionableError {
    ActionableError actionableError();
}
