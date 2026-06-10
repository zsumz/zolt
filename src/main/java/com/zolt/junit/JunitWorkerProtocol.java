package com.zolt.junit;

import com.zolt.build.TestSelection;
import com.zolt.build.TestSelectionCodec;
import com.zolt.build.TestSelectionException;
import java.nio.file.Path;
import java.util.List;

public final class JunitWorkerProtocol {
    public static final String RESULT_PREFIX = "ZOLT_WORKER_RESULT";

    private JunitWorkerProtocol() {
    }

    public static String runRequest(String requestId, Path testOutputDirectory) {
        return runRequest(requestId, testOutputDirectory, TestSelection.empty());
    }

    public static String runRequest(String requestId, Path testOutputDirectory, TestSelection testSelection) {
        if (testOutputDirectory == null) {
            throw new IllegalArgumentException("JUnit worker test output directory is required.");
        }
        TestSelection selection = testSelection == null ? TestSelection.empty() : testSelection;
        String path = testOutputDirectory.toString();
        validateField("JUnit worker test output directory", path);
        String prefix = "RUN\t" + validateRequestId(requestId) + "\t" + path;
        if (selection.emptySelection()) {
            return prefix;
        }
        return prefix
                + "\t"
                + selectionField("JUnit worker class selectors", TestSelectionCodec.encodeStrings(selection.classSelectors()))
                + "\t"
                + selectionField("JUnit worker method selectors", TestSelectionCodec.encodeMethods(selection.methodSelectors()))
                + "\t"
                + selectionField("JUnit worker class-name patterns", TestSelectionCodec.encodeStrings(selection.classNamePatterns()))
                + "\t"
                + selectionField("JUnit worker included tags", TestSelectionCodec.encodeStrings(selection.includedTags()))
                + "\t"
                + selectionField("JUnit worker excluded tags", TestSelectionCodec.encodeStrings(selection.excludedTags()));
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
            return new WorkerRequest(WorkerCommand.QUIT, requestId, "", TestSelection.empty());
        }
        if (!"RUN".equals(command)) {
            throw new IllegalArgumentException("Unknown JUnit worker request command `" + command + "`.");
        }
        if ((parts.length != 3 && parts.length != 8) || parts[2].isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker run request. Expected RUN<TAB>requestId<TAB>testOutputDirectory"
                            + "<TAB>classSelectors<TAB>methodSelectors<TAB>classNamePatterns<TAB>includedTags<TAB>excludedTags.");
        }
        return new WorkerRequest(
                WorkerCommand.RUN,
                requestId,
                validateField("JUnit worker test output directory", parts[2]),
                parts.length == 3 ? TestSelection.empty() : parseSelection(parts));
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

    private static String selectionField(String name, String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must not contain tabs or newlines.");
        }
        return value;
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

    private static TestSelection parseSelection(String[] parts) {
        try {
            List<String> classSelectors = TestSelectionCodec.decodeStrings("JUnit worker class selectors", parts[3]);
            List<TestSelection.MethodSelector> methodSelectors =
                    TestSelectionCodec.decodeMethods("JUnit worker method selectors", parts[4]);
            List<String> patterns = TestSelectionCodec.decodeStrings("JUnit worker class-name patterns", parts[5]);
            List<String> includedTags = TestSelectionCodec.decodeStrings("JUnit worker included tags", parts[6]);
            List<String> excludedTags = TestSelectionCodec.decodeStrings("JUnit worker excluded tags", parts[7]);
            return TestSelection.fromFields(classSelectors, methodSelectors, patterns, includedTags, excludedTags);
        } catch (IllegalArgumentException | TestSelectionException exception) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker test selection. " + exception.getMessage(),
                    exception);
        }
    }

    public enum WorkerCommand {
        RUN,
        QUIT
    }

    public record WorkerRequest(
            WorkerCommand command,
            String requestId,
            String testOutputDirectory,
            TestSelection testSelection) {
    }

    public record WorkerResult(String requestId, int exitCode) {
    }
}
