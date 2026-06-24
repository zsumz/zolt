package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cli.console.ConsoleStyle;
import org.junit.jupiter.api.Test;

final class HelpOptionHighlighterTest {
    @Test
    void highlightsOnlyOptionTokensInOptionLines() {
        String input = String.join(
                "\n",
                "      --test=<testSelectors>",
                "  -q, --quiet               Suppress output.",
                "         Use --tests for class-name patterns.");

        assertEquals(
                String.join(
                        "\n",
                        "      \u001B[32m--test\u001B[0m=<testSelectors>",
                        "  \u001B[32m-q\u001B[0m, \u001B[32m--quiet\u001B[0m               Suppress output.",
                        "         Use --tests for class-name patterns."),
                HelpOptionHighlighter.highlight(input, ConsoleStyle.enabled()));
    }

    @Test
    void disabledStyleLeavesTextPlain() {
        String input = "      --workspace          Build every member.";

        assertEquals(input, HelpOptionHighlighter.highlight(input, ConsoleStyle.disabled()));
    }
}
