package com.zolt.cli.console;

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

        writer.result("Resolved 87 packages");

        assertEquals("\u001B[32mResolved\u001B[0m 87 packages\n", stderr.toString());
    }
}
