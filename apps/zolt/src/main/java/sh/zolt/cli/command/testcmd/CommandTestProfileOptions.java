package sh.zolt.cli.command.testcmd;

import sh.zolt.build.profile.TestProfileSettings;
import java.nio.file.Path;
import picocli.CommandLine.Option;

final class CommandTestProfileOptions {
    @Option(names = "--profile-tests", description = "Write opt-in test profile JSON for supported JUnit worker runs.")
    private boolean profileTests;

    @Option(names = "--profile-dir", description = "Write test profile evidence to a project-relative directory.")
    private Path profileDir;

    @Option(names = "--profile-top", description = "Limit printed slow test and class profile rows.")
    private Integer profileTop;

    @Option(names = "--profile-min", description = "Suppress profile summary rows below a duration such as 250ms, 3s, or 1m.")
    private String profileMin;

    TestProfileSettings settings() {
        return TestProfileSettings.fromCli(profileTests, profileDir, profileTop, profileMin);
    }
}
