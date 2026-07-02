package sh.zolt.cli.command.testcmd;

import sh.zolt.build.profile.TestProfileSettings;
import sh.zolt.build.profile.TestProfileSummaryFormatter;
import sh.zolt.build.testruntime.TestRunResult;
import sh.zolt.cli.CommandHumanOutput;
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
