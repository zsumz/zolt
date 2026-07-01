package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cli.console.ConsoleStyle;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class CommandHumanOutputTest {
    @Test
    void pointersEmitsOneArrowLinePerTargetInOrder() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.disabled(), false);

        output.pointers("wrote", "dist/app.tar.gz", "dist/app.tar.gz.sha256", "dist/manifest.json");

        assertEquals(
                """
                  → wrote dist/app.tar.gz
                  → wrote dist/app.tar.gz.sha256
                  → wrote dist/manifest.json
                """,
                buffer.toString());
    }

    @Test
    void pointersColorsTheArrowAndPathWhenStyleIsEnabled() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.enabled(), false);

        output.pointers("wrote", "dist/app.tar.gz");

        assertEquals(
                "  \u001B[36m→\u001B[0m wrote \u001B[36mdist/app.tar.gz\u001B[0m\n",
                buffer.toString());
    }

    @Test
    void pointersPrintsNothingWhenQuiet() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.disabled(), true);

        output.pointers("wrote", "dist/app.tar.gz", "dist/manifest.json");

        assertEquals("", buffer.toString());
    }
}
