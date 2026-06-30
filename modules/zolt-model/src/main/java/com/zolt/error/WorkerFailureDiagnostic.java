package com.zolt.error;

/**
 * Formats a worker failure as a single, parent-parseable diagnostic line instead of a raw
 * multi-line {@code printStackTrace} dump.
 *
 * <p>Zolt forks framework code into JVM subprocesses (workers). When a worker fails it prints a
 * leading {@code error: <what to do next>} line and then needs to surface the originating cause.
 * The parent launchers merge the worker's combined stream and embed it verbatim into a user-facing
 * exception, so a raw stack trace turns into an unstructured blob that violates Zolt's "boring and
 * explicit" output convention. This helper collapses the cause into one deterministic line:
 *
 * <pre>{@code cause: <ExceptionClass>: <message> [at frame; frame; frame]}</pre>
 *
 * <p>The frame digest keeps at most {@link #MAX_DIGEST_FRAMES} top frames in their natural order so
 * golden/output tests stay reproducible. The line is always single-line: newlines in the message
 * are flattened.
 */
public final class WorkerFailureDiagnostic {
    static final int MAX_DIGEST_FRAMES = 3;

    private WorkerFailureDiagnostic() {
    }

    /**
     * Builds the single-line cause diagnostic for {@code failure}. Returns {@code "cause: <unknown>"}
     * when {@code failure} is {@code null} so callers never have to null-check before printing.
     */
    public static String causeLine(Throwable failure) {
        if (failure == null) {
            return "cause: <unknown>";
        }
        StringBuilder line = new StringBuilder("cause: ").append(failure.getClass().getName());
        String message = flatten(failure.getMessage());
        if (!message.isBlank()) {
            line.append(": ").append(message);
        }
        String digest = frameDigest(failure.getStackTrace());
        if (!digest.isEmpty()) {
            line.append(" [at ").append(digest).append(']');
        }
        return line.toString();
    }

    private static String frameDigest(StackTraceElement[] frames) {
        if (frames == null || frames.length == 0) {
            return "";
        }
        StringBuilder digest = new StringBuilder();
        int count = Math.min(MAX_DIGEST_FRAMES, frames.length);
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                digest.append("; ");
            }
            digest.append(frames[index]);
        }
        if (frames.length > count) {
            digest.append("; ... (").append(frames.length - count).append(" more)");
        }
        return digest.toString();
    }

    private static String flatten(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\r', ' ').replace('\n', ' ').strip();
    }
}
