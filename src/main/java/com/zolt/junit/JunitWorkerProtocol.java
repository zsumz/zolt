package com.zolt.junit;

import java.nio.file.Path;

public final class JunitWorkerProtocol {
    public static final String RESULT_PREFIX = "ZOLT_WORKER_RESULT";

    private JunitWorkerProtocol() {
    }

    public static String runRequest(String requestId, Path testOutputDirectory) {
        if (testOutputDirectory == null) {
            throw new IllegalArgumentException("JUnit worker test output directory is required.");
        }
        String path = testOutputDirectory.toString();
        validateField("JUnit worker test output directory", path);
        return "RUN\t" + validateRequestId(requestId) + "\t" + path;
    }

    public static String quitRequest(String requestId) {
        return "QUIT\t" + validateRequestId(requestId);
    }

    public static WorkerRequest parseRequest(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker request. Expected RUN<TAB>requestId<TAB>testOutputDirectory or QUIT<TAB>requestId.");
        }
        String command = parts[0];
        String requestId = validateRequestId(parts[1]);
        if ("QUIT".equals(command)) {
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed JUnit worker quit request. Expected QUIT<TAB>requestId.");
            }
            return new WorkerRequest(WorkerCommand.QUIT, requestId, "");
        }
        if (!"RUN".equals(command)) {
            throw new IllegalArgumentException("Unknown JUnit worker request command `" + command + "`.");
        }
        if (parts.length != 3 || parts[2].isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker run request. Expected RUN<TAB>requestId<TAB>testOutputDirectory.");
        }
        return new WorkerRequest(
                WorkerCommand.RUN,
                requestId,
                validateField("JUnit worker test output directory", parts[2]));
    }

    public static String result(String requestId, int exitCode) {
        return RESULT_PREFIX + "\t" + validateRequestId(requestId) + "\t" + exitCode;
    }

    public static WorkerResult parseResult(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 3 || !RESULT_PREFIX.equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker result. Expected ZOLT_WORKER_RESULT<TAB>requestId<TAB>exitCode.");
        }
        try {
            return new WorkerResult(validateRequestId(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker result exit code `" + parts[2] + "`.",
                    exception);
        }
    }

    private static String validateRequestId(String requestId) {
        return validateField("JUnit worker request id", requestId);
    }

    private static String validateField(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must not contain tabs or newlines.");
        }
        return value;
    }

    public enum WorkerCommand {
        RUN,
        QUIT
    }

    public record WorkerRequest(WorkerCommand command, String requestId, String testOutputDirectory) {
    }

    public record WorkerResult(String requestId, int exitCode) {
    }
}
