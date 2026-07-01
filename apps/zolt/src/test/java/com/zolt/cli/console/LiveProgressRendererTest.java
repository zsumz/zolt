package com.zolt.cli.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LiveProgressRendererTest {
    private static final String HIDE_CURSOR = "[?25l";
    private static final String SHOW_CURSOR = "[?25h";
    private static final String CLEAR_TO_EOL = "[K";

    @Test
    void interactivePhaseAnimatesInPlaceAndCommitsSuccessLine() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);

        assertTrue(writer.animated(), "interactive TTY with progress enabled must animate");

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.done();

        String output = stderr.toString();
        assertTrue(output.startsWith(HIDE_CURSOR), "phase must hide the cursor at start: " + escape(output));
        assertTrue(output.contains("\r"), "live line must redraw with carriage return");
        assertTrue(output.contains(CLEAR_TO_EOL), "live line must clear to end of line");
        assertTrue(output.contains("Resolving dependencies"), "spinner frame carries the phase name");
        assertTrue(output.contains("✔ Resolving dependencies"), "success commits a checkmark line");
        assertTrue(output.endsWith(SHOW_CURSOR), "cursor must be restored after the phase: " + escape(output));
        assertFalse(output.contains("✗"), "success must not emit a failure glyph");
    }

    @Test
    void interactivePhaseCommitsFailureLineAndRestoresCursor() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.fail();

        String output = stderr.toString();
        assertTrue(output.contains(HIDE_CURSOR), "phase must hide the cursor at start");
        assertTrue(output.contains("✗ Resolving dependencies"), "failure commits a cross line");
        assertFalse(output.contains("✔"), "failure must not emit a success glyph");
        assertTrue(output.endsWith(SHOW_CURSOR), "cursor must be restored on failure: " + escape(output));
    }

    @Test
    void interactiveSpinnerFramesAreDrawnFromTheBrailleCycle() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);

        ProgressPhase phase = writer.phase("Building project");
        phase.done();

        String output = stderr.toString();
        // The initial frame (index 0) is rendered synchronously before the ticker thread starts.
        assertTrue(output.contains(SpinnerTicker.frame(0) + " Building project"), "first frame draws ⠋ + name");
    }

    @Test
    void nonInteractivePhaseEmitsAppendOnlyStartLineWithNoCursorControl() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, false, Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);

        assertFalse(writer.animated(), "non-interactive stderr must never animate");

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.done();
        writer.result("Resolved 87 packages");

        assertEquals(
                """
                Resolving dependencies...
                Resolved 87 packages
                """,
                stderr.toString());
    }

    @Test
    void parseablePhaseIsByteIdenticalToSuppressedFallback() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of()),
                ConsoleStyle.enabled(),
                ProgressOutputContract.PARSEABLE);

        assertFalse(writer.animated(), "PARSEABLE output must never animate even on a TTY");

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.fail();

        assertEquals("", stderr.toString(), "PARSEABLE progress stays fully suppressed");
    }

    @Test
    void progressNeverPhaseEmitsNothingEvenOnInteractiveStderr() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.NEVER, false, true, Map.of()),
                ConsoleStyle.enabled(),
                ProgressOutputContract.HUMAN);

        assertFalse(writer.animated(), "--progress=never must never animate");

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.done();

        assertEquals("", stderr.toString(), "--progress=never keeps stderr empty");
    }

    @Test
    void repeatedDoneAndFailAreNoOps() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);

        ProgressPhase phase = writer.phase("Resolving dependencies");
        phase.done();
        String afterFirst = stderr.toString();
        phase.done();
        phase.fail();

        assertEquals(afterFirst, stderr.toString(), "finishing a phase twice must not emit more output");
    }

    private static ProgressWriter interactiveWriter(StringWriter stderr) {
        return new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);
    }

    private static String escape(String value) {
        return value.replace("", "\\u001B").replace("\r", "\\r");
    }
}
