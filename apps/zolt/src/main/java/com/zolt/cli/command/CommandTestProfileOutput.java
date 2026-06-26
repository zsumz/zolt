package com.zolt.cli.command;

import com.zolt.build.TestProfileSettings;
import com.zolt.build.TestProfileSummaryFormatter;
import com.zolt.build.TestRunResult;
import com.zolt.cli.CommandHumanOutput;

final class CommandTestProfileOutput {
    private CommandTestProfileOutput() {
    }

    static void print(CommandHumanOutput output, TestRunResult result, TestProfileSettings settings) {
        result.profileDirectory()
                .flatMap(directory -> TestProfileSummaryFormatter.format(directory.resolve("profile.json"), settings))
                .ifPresent(summary -> {
                    output.blankLine();
                    output.line(summary);
                });
        result.profileDirectory().ifPresent(directory ->
                output.detail("Wrote test profile to " + directory.resolve("profile.json")));
    }
}
