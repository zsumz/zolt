package com.zolt.cli.command.testcmd;

import com.zolt.build.profile.TestProfileSettings;
import com.zolt.build.profile.TestProfileSummaryFormatter;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.cli.CommandHumanOutput;
import java.nio.file.Path;

final class CommandTestProfileOutput {
    private CommandTestProfileOutput() {
    }

    static void print(CommandHumanOutput output, TestRunResult result, TestProfileSettings settings) {
        result.profileDirectory().ifPresent(directory -> print(output, directory, settings));
    }

    static void print(CommandHumanOutput output, Path profileDirectory, TestProfileSettings settings) {
        TestProfileSummaryFormatter.format(profileDirectory.resolve("profile.json"), settings)
                .ifPresent(summary -> {
                    output.blankLine();
                    output.line(summary);
                });
        output.detail("Wrote test profile to " + profileDirectory.resolve("profile.json"));
    }
}
