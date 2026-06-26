package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cli.console.ConsoleStyle;
import org.junit.jupiter.api.Test;

final class HelpParameterHighlighterTest {
    @Test
    void highlightsPositionalParameterLabels() {
        String input = String.join(
                "\n",
                "      [ARGS...]             Arguments passed to the application after `--`.",
                "      ARCHIVE...            Release archive to verify.",
                "      GROUP:ARTIFACT        Platform BOM coordinate.",
                "      GROUP:ARTIFACT[:VERSION]",
                "                            Platform BOM coordinate.");

        assertEquals(
                String.join(
                        "\n",
                        "      \u001B[36m[ARGS...]\u001B[0m             Arguments passed to the application after `--`.",
                        "      \u001B[36mARCHIVE...\u001B[0m            Release archive to verify.",
                        "      \u001B[36mGROUP:ARTIFACT\u001B[0m        Platform BOM coordinate.",
                        "      \u001B[36mGROUP:ARTIFACT[:VERSION]\u001B[0m",
                        "                            Platform BOM coordinate."),
                HelpParameterHighlighter.highlight(input, ConsoleStyle.enabled()));
    }

    @Test
    void disabledStyleLeavesTextPlain() {
        String input = "      [ARGS...]             Arguments passed to the application after `--`.";

        assertEquals(input, HelpParameterHighlighter.highlight(input, ConsoleStyle.disabled()));
    }
}
