package sh.zolt.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ActionableErrorTest {
    @Test
    void trimsSummaryAndRemediationAndBuildsFlatMessage() {
        ActionableError error = ActionableError.of(" lockfile is stale ", " run `zolt lock` ");

        assertEquals("lockfile is stale", error.summary());
        assertEquals("run `zolt lock`", error.remediation());
        assertEquals("lockfile is stale run `zolt lock`", error.message());
        assertFalse(error.cause().isPresent());
    }

    @Test
    void carriesOptionalCauseThroughActionableException() {
        IllegalStateException cause = new IllegalStateException("network unavailable");
        ActionableError error = ActionableError.of("Resolve failed.", "Check repository credentials.", cause);
        ActionableException exception = new ActionableException(error);

        assertSame(error, exception.error());
        assertSame(error, exception.actionableError());
        assertSame(cause, exception.getCause());
        assertEquals("Resolve failed. Check repository credentials.", exception.getMessage());
    }

    @Test
    void rejectsBlankErrorPartsAndNullExceptionCarrier() {
        assertEquals(
                "ActionableError summary must not be blank.",
                assertThrows(IllegalArgumentException.class, () -> ActionableError.of(" ", "fix it"))
                        .getMessage());
        assertEquals(
                "ActionableError remediation must not be blank; every error must explain what to do next.",
                assertThrows(IllegalArgumentException.class, () -> ActionableError.of("failed", "\t"))
                        .getMessage());
        assertEquals(
                "ActionableException requires a non-null ActionableError.",
                assertThrows(IllegalArgumentException.class, () -> new ActionableException(null))
                        .getMessage());
    }

    @Test
    void workerFailureDiagnosticIsSingleLineWithBoundedFrameDigest() {
        RuntimeException failure = new RuntimeException("first line\nsecond line");
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("Example", "one", "Example.java", 1),
            new StackTraceElement("Example", "two", "Example.java", 2),
            new StackTraceElement("Example", "three", "Example.java", 3),
            new StackTraceElement("Example", "four", "Example.java", 4),
            new StackTraceElement("Example", "five", "Example.java", 5)
        });

        String line = WorkerFailureDiagnostic.causeLine(failure);

        assertTrue(line.startsWith("cause: java.lang.RuntimeException: first line second line [at "));
        assertTrue(line.contains("Example.one(Example.java:1); Example.two(Example.java:2); Example.three(Example.java:3)"));
        assertTrue(line.endsWith("; ... (2 more)]"));
        assertFalse(line.contains("\n"));
    }

    @Test
    void workerFailureDiagnosticHandlesMissingFailureAndMessage() {
        RuntimeException failure = new RuntimeException();
        failure.setStackTrace(new StackTraceElement[0]);

        assertEquals("cause: <unknown>", WorkerFailureDiagnostic.causeLine(null));
        assertEquals("cause: java.lang.RuntimeException", WorkerFailureDiagnostic.causeLine(failure));
    }
}
