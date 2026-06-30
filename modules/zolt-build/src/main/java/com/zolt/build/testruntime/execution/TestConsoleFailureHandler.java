package com.zolt.build.testruntime.execution;

import com.zolt.build.JavaRunException;
import com.zolt.test.runtime.TestRunException;
import com.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.Optional;

final class TestConsoleFailureHandler {
    void throwForFailedRun(
            JavaRunException exception,
            TestSelection selection,
            Optional<Path> reportsDirectory) {
        if (!selection.emptySelection() && noTestsFound(exception.getMessage())) {
            throw noSelectedTestsMatched(exception.getMessage(), exception);
        }
        if (reportsDirectory.isEmpty()) {
            throw exception;
        }
        throw testFailed(exception, reportsDirectory);
    }

    void throwIfSelectedTestsDidNotMatch(String output, TestSelection selection) {
        if (!selection.emptySelection() && noTestsFound(output)) {
            throw noSelectedTestsMatched(output, null);
        }
    }

    private static boolean noTestsFound(String message) {
        return message.contains("No tests found")
                || message.contains("Tests found: 0")
                || message.contains("[         0 tests found");
    }

    private static TestRunException noSelectedTestsMatched(String output, Throwable cause) {
        String message = "Selected tests did not match any tests. "
                + "Check --test, --tests, --include-tag, and --exclude-tag values, then run `zolt test` again.\n"
                + output.stripTrailing();
        return cause == null ? new TestRunException(message) : new TestRunException(message, cause);
    }

    private static TestRunException testFailed(JavaRunException exception, Optional<Path> reportsDirectory) {
        String message = exception.getMessage() + "\nTest reports: " + reportsDirectory.orElseThrow();
        return new TestRunException(message, exception);
    }
}
