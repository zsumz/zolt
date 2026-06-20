package com.zolt.junit;

import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionCodec;
import com.zolt.test.TestSelectionException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class JunitWorkerProtocol {
    public static final String RESULT_PREFIX = "ZOLT_WORKER_RESULT";

    private JunitWorkerProtocol() {
    }

    public static String runRequest(String requestId, Path testOutputDirectory) {
        return runRequest(requestId, testOutputDirectory, TestSelection.empty());
    }

    public static String runRequest(String requestId, Path testOutputDirectory, TestSelection testSelection) {
        return runRequest(requestId, testOutputDirectory, testSelection, Optional.empty(), List.of());
    }

    public static String runRequest(
            String requestId,
            Path testOutputDirectory,
            TestSelection testSelection,
            Optional<Path> reportsDirectory,
            List<String> events) {
        if (testOutputDirectory == null) {
            throw new IllegalArgumentException("JUnit worker test output directory is required.");
        }
        TestSelection selection = testSelection == null ? TestSelection.empty() : testSelection;
        String path = testOutputDirectory.toString();
        validateField("JUnit worker test output directory", path);
        String prefix = "RUN\t" + validateRequestId(requestId) + "\t" + path;
        Optional<Path> reportPath = reportsDirectory == null ? Optional.empty() : reportsDirectory;
        List<String> requestedEvents = events == null ? List.of() : List.copyOf(events);
        if (selection.emptySelection() && reportPath.isEmpty() && requestedEvents.isEmpty()) {
            return prefix;
        }
        if (reportPath.isEmpty() && requestedEvents.isEmpty()) {
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
        return prefix
                + "\t"
                + optionalPathField("JUnit worker reports directory", reportPath)
                + "\t"
                + selectionField("JUnit worker events", TestSelectionCodec.encodeStrings(requestedEvents))
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
            return new WorkerRequest(WorkerCommand.QUIT, requestId, "", Optional.empty(), List.of(), TestSelection.empty());
        }
        if (!"RUN".equals(command)) {
            throw new IllegalArgumentException("Unknown JUnit worker request command `" + command + "`.");
        }
        if ((parts.length != 3 && parts.length != 8 && parts.length != 10) || parts[2].isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker run request. Expected RUN<TAB>requestId<TAB>testOutputDirectory"
                            + "<TAB>reportsDirectory<TAB>events"
                            + "<TAB>classSelectors<TAB>methodSelectors<TAB>classNamePatterns<TAB>includedTags<TAB>excludedTags.");
        }
        return new WorkerRequest(
                WorkerCommand.RUN,
                requestId,
                validateField("JUnit worker test output directory", parts[2]),
                reportsDirectory(parts),
                events(parts),
                testSelection(parts));
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
            int offset = parts.length == 10 ? 5 : 3;
            List<String> classSelectors = TestSelectionCodec.decodeStrings("JUnit worker class selectors", parts[offset]);
            List<TestSelection.MethodSelector> methodSelectors =
                    TestSelectionCodec.decodeMethods("JUnit worker method selectors", parts[offset + 1]);
            List<String> patterns = TestSelectionCodec.decodeStrings("JUnit worker class-name patterns", parts[offset + 2]);
            List<String> includedTags = TestSelectionCodec.decodeStrings("JUnit worker included tags", parts[offset + 3]);
            List<String> excludedTags = TestSelectionCodec.decodeStrings("JUnit worker excluded tags", parts[offset + 4]);
            return TestSelection.fromFields(classSelectors, methodSelectors, patterns, includedTags, excludedTags);
        } catch (IllegalArgumentException | TestSelectionException exception) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker test selection. " + exception.getMessage(),
                    exception);
        }
    }

    private static Optional<String> reportsDirectory(String[] parts) {
        if (parts.length != 10 || parts[3].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(validateField("JUnit worker reports directory", parts[3]));
    }

    private static List<String> events(String[] parts) {
        if (parts.length != 10) {
            return List.of();
        }
        return TestSelectionCodec.decodeStrings("JUnit worker events", parts[4]);
    }

    private static TestSelection testSelection(String[] parts) {
        return parts.length == 3 ? TestSelection.empty() : parseSelection(parts);
    }

    private static String optionalPathField(String name, Optional<Path> value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return validateField(name, value.orElseThrow().toString());
    }

    public enum WorkerCommand {
        RUN,
        QUIT
    }

    public record WorkerRequest(
            WorkerCommand command,
            String requestId,
            String testOutputDirectory,
            Optional<String> reportsDirectory,
            List<String> events,
            TestSelection testSelection) {
        public WorkerRequest {
            reportsDirectory = reportsDirectory == null ? Optional.empty() : reportsDirectory;
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    public record WorkerResult(String requestId, int exitCode) {
    }
}
