package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cli.console.ConsoleStyle;
import org.junit.jupiter.api.Test;

final class HelpSynopsisHighlighterTest {
    @Test
    void highlightsCommandPathOptionsAndMetavars() {
        String input = String.join(
                "\n",
                "zolt run-package [-hqV] [--color <WHEN>] [COMMAND]",
                "          <memberGroups>...]]... [--test <testSelectors>]...");

        assertEquals(
                String.join(
                        "\n",
                        "\u001B[1;36mzolt run-package\u001B[0m \u001B[36m[\u001B[0m\u001B[1;36m-hqV\u001B[0m\u001B[36m]\u001B[0m \u001B[36m[\u001B[0m\u001B[1;36m--color\u001B[0m \u001B[36m<WHEN>]\u001B[0m \u001B[36m[COMMAND]\u001B[0m",
                        "          \u001B[36m<memberGroups>...]]...\u001B[0m \u001B[36m[\u001B[0m\u001B[1;36m--test\u001B[0m \u001B[36m<testSelectors>]...\u001B[0m"),
                HelpSynopsisHighlighter.highlight(input, ConsoleStyle.enabled()));
    }

    @Test
    void disabledStyleLeavesSynopsisPlain() {
        String input = "zolt test [--color <WHEN>]";

        assertEquals(input, HelpSynopsisHighlighter.highlight(input, ConsoleStyle.disabled()));
    }
}
