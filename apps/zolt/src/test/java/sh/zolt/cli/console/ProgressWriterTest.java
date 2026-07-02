package sh.zolt.cli.console;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProgressWriterTest {
    @Test
    void writerEmitsStableLineShapesToStderrWhenEnabled() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, false, Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);

        writer.start("Resolving dependencies");
        writer.step("Downloaded artifacts 3/17");
        writer.heartbeat("Still running: Native Image");
        writer.result("Resolved 87 packages");

        assertEquals(
                """
                Resolving dependencies...
                Downloaded artifacts 3/17
                Still running: Native Image
                Resolved 87 packages
                """,
                stderr.toString());
    }

    @Test
    void writerSuppressesParseableAndDisabledProgress() {
        StringWriter parseable = new StringWriter();
        StringWriter disabled = new StringWriter();

        new ProgressWriter(
                        new PrintWriter(parseable),
                        ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of()),
                        ConsoleStyle.enabled(),
                        ProgressOutputContract.PARSEABLE)
                .start("Resolving dependencies");
        new ProgressWriter(
                        new PrintWriter(disabled),
                        ProgressPolicy.of(ProgressMode.NEVER, false, true, Map.of()),
                        ConsoleStyle.enabled(),
                        ProgressOutputContract.HUMAN)
                .result("Resolved 87 packages");

        assertEquals("", parseable.toString());
        assertEquals("", disabled.toString());
    }

    @Test
    void writerStylesOnlyTheLeadFragmentWhenColorIsEnabled() {
        StringWriter stderr = new StringWriter();
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of()),
                ConsoleStyle.enabled(),
                ProgressOutputContract.HUMAN);

        writer.start("Building project");
        writer.step("Downloaded artifacts 3/17");
        writer.heartbeat("Still running: Native Image");
        writer.result("Resolved 87 packages");

        assertEquals(
                """
                \u001B[36mBuilding\u001B[0m project...
                \u001B[36mDownloaded\u001B[0m artifacts 3/17
                \u001B[36mStill\u001B[0m running: Native Image
                \u001B[32mResolved\u001B[0m 87 packages
                """,
                stderr.toString());
    }

    @Test
    void phaseFallbackMatchesStartLineOnNonInteractiveStderr() {
        StringWriter viaPhase = new StringWriter();
        StringWriter viaStart = new StringWriter();

        writer(viaPhase, false).phase("Resolving dependencies").done();
        writer(viaStart, false).start("Resolving dependencies");

        assertEquals("Resolving dependencies...\n", viaPhase.toString());
        assertEquals(viaStart.toString(), viaPhase.toString());
    }

    @Test
    void animatedIsGatedOnInteractiveStderr() {
        assertEquals(false, writer(new StringWriter(), false).animated());
        assertEquals(true, writer(new StringWriter(), true).animated());
    }

    private static ProgressWriter writer(StringWriter stderr, boolean interactiveStderr) {
        return new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, interactiveStderr, Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);
    }

    @Test
    void writerKeepsNoColorScopedToColorWhenProgressIsForced() {
        StringWriter stderr = new StringWriter();
        Map<String, String> environment = Map.of("NO_COLOR", "1");
        ProgressWriter writer = new ProgressWriter(
                new PrintWriter(stderr),
                ProgressPolicy.of(ProgressMode.ALWAYS, false, true, environment),
                ConsoleStyle.of(ColorMode.AUTO, true, environment),
                ProgressOutputContract.HUMAN);

        writer.start("Resolving dependencies");
        writer.result("Resolved 87 packages");

        assertEquals(
                """
                Resolving dependencies...
                Resolved 87 packages
                """,
                stderr.toString());
    }
}
