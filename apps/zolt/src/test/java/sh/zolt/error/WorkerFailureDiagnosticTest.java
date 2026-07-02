package sh.zolt.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorkerFailureDiagnosticTest {
    @Test
    void formatsSingleLineCauseWithClassMessageAndDigest() {
        IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("com.example.Worker", "run", "Worker.java", 12),
            new StackTraceElement("com.example.Worker", "main", "Worker.java", 3),
        });

        String line = WorkerFailureDiagnostic.causeLine(failure);

        assertEquals(1, line.lines().count(), line);
        assertTrue(line.startsWith("cause: java.lang.IllegalStateException: boom [at "), line);
        assertTrue(line.contains("com.example.Worker.run(Worker.java:12)"), line);
    }

    @Test
    void capsTheFrameDigestAndCountsTheRemainder() {
        RuntimeException failure = new RuntimeException("deep");
        StackTraceElement[] frames = new StackTraceElement[5];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement("com.example.Worker", "frame" + index, "Worker.java", index);
        }
        failure.setStackTrace(frames);

        String line = WorkerFailureDiagnostic.causeLine(failure);

        assertEquals(1, line.lines().count(), line);
        assertTrue(line.contains("frame0"), line);
        assertTrue(line.contains("frame1"), line);
        assertTrue(line.contains("frame2"), line);
        assertFalse(line.contains("frame3"), line);
        assertTrue(line.contains("... (2 more)"), line);
    }

    @Test
    void flattensMultiLineMessagesToASingleLine() {
        RuntimeException failure = new RuntimeException("line one\nline two");
        failure.setStackTrace(new StackTraceElement[0]);

        String line = WorkerFailureDiagnostic.causeLine(failure);

        assertEquals(1, line.lines().count(), line);
        assertEquals("cause: java.lang.RuntimeException: line one line two", line);
    }

    @Test
    void omitsMessageWhenAbsent() {
        RuntimeException failure = new RuntimeException();
        failure.setStackTrace(new StackTraceElement[0]);

        assertEquals("cause: java.lang.RuntimeException", WorkerFailureDiagnostic.causeLine(failure));
    }

    @Test
    void returnsUnknownForNullFailure() {
        assertEquals("cause: <unknown>", WorkerFailureDiagnostic.causeLine(null));
    }
}
