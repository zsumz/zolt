package com.zolt.cli.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.resolve.progress.ArtifactProgressListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LiveProgressRendererTest {
    private static final String HIDE_CURSOR = "[?25l";
    private static final String SHOW_CURSOR = "[?25h";
    private static final String CLEAR_TO_EOL = "[K";
    private static final Coordinate APP = new Coordinate("com.example", "app", Optional.of("1.0.0"));

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

    @Test
    void interactiveArtifactProgressRendersLiveStartAndCommitsCompletionLine() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);
        ArtifactProgressListener listener = writer.artifactProgressListener();

        ProgressPhase phase = writer.phase("Resolving dependencies");
        listener.onStart(ArtifactDescriptor.jar(APP));
        listener.onComplete(ArtifactDescriptor.jar(APP), 1536L);
        phase.done();

        String output = stderr.toString();
        assertTrue(output.contains("⠋ com.example:app:1.0.0"), "start event renders artifact row: " + escape(output));
        assertTrue(
                output.contains("✔ com.example:app:1.0.0 1.5 KiB"),
                "complete event commits artifact row: " + escape(output));
        assertTrue(output.contains("✔ Resolving dependencies"), "phase summary still commits after artifact rows");
        assertTrue(output.endsWith(SHOW_CURSOR), "cursor must be restored after artifact progress: " + escape(output));
    }

    @Test
    void interactiveArtifactProgressRendersByteBarForLargeKnownDownloads() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);
        ArtifactDescriptor descriptor = ArtifactDescriptor.jar(APP);
        ArtifactProgressListener listener = writer.artifactProgressListener();

        ProgressPhase phase = writer.phase("Resolving dependencies");
        listener.onStart(descriptor);
        listener.onBytes(descriptor, 64L * 1024L, 128L * 1024L);
        listener.onComplete(descriptor, 128L * 1024L);
        phase.done();

        String output = stderr.toString();
        assertTrue(
                output.contains("▓▓▓▓▓▒▒▒▒▒ com.example:app:1.0.0 64.0 KiB / 128.0 KiB"),
                "known large downloads render a byte bar: " + escape(output));
        assertTrue(
                output.contains("✔ com.example:app:1.0.0 128.0 KiB"),
                "completion still commits the final byte count: " + escape(output));
    }

    @Test
    void interactiveArtifactProgressKeepsSmallDownloadsOnSpinnerPath() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);
        ArtifactDescriptor descriptor = ArtifactDescriptor.jar(APP);
        ArtifactProgressListener listener = writer.artifactProgressListener();

        ProgressPhase phase = writer.phase("Resolving dependencies");
        listener.onStart(descriptor);
        listener.onBytes(descriptor, 512L, 1024L);
        listener.onComplete(descriptor, 1024L);
        phase.done();

        String output = stderr.toString();
        assertFalse(output.contains("▓"), "small downloads stay spinner to check: " + escape(output));
        assertTrue(output.contains("✔ com.example:app:1.0.0 1.0 KiB"));
    }

    @Test
    void interactiveArtifactByteProgressIsThrottled() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = interactiveWriter(stderr);
        ArtifactDescriptor descriptor = ArtifactDescriptor.jar(APP);
        ArtifactProgressListener listener = writer.artifactProgressListener();

        ProgressPhase phase = writer.phase("Resolving dependencies");
        listener.onStart(descriptor);
        for (int i = 0; i < 20; i++) {
            listener.onBytes(descriptor, (512L * 1024L) + i, 1024L * 1024L);
        }
        phase.done();

        String output = stderr.toString();
        int barFrames = occurrences(output, " / 1.0 MiB");
        assertTrue(barFrames >= 1, "first byte event should render a bar: " + escape(output));
        assertTrue(barFrames < 20, "rapid byte events should be coalesced: " + escape(output));
    }

    @Test
    void nonInteractiveArtifactProgressIsNoop() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, false, Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);

        ProgressPhase phase = writer.phase("Resolving dependencies");
        writer.artifactProgressListener().onStart(ArtifactDescriptor.jar(APP));
        writer.artifactProgressListener().onBytes(ArtifactDescriptor.jar(APP), 64L * 1024L, 128L * 1024L);
        writer.artifactProgressListener().onComplete(ArtifactDescriptor.jar(APP), 1536L);
        phase.done();

        assertEquals("Resolving dependencies...\n", stderr.toString());
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

    private static int occurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while (true) {
            int next = value.indexOf(token, index);
            if (next < 0) {
                return count;
            }
            count++;
            index = next + token.length();
        }
    }
}
