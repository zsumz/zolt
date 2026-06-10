package org.apache.commons.cli;

/**
 * Signals invalid command-line input.
 */
public final class ParseException extends Exception {
    /**
     * Creates a parse exception.
     *
     * @param message user-facing parse message
     */
    public ParseException(String message) {
        super(message);
    }
}
