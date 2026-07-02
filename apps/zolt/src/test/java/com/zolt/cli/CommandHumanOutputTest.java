package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zolt.cli.console.ConsoleStyle;
import com.zolt.provenance.BuildProvenance;
import com.zolt.provenance.GitProvenance;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
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

    @Test
    void provenancePrintsNothingWhenNotVerbose() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.disabled(), false);

        output.provenance(provenance(git(), Optional.of("sha256:inputs")));

        assertEquals("", buffer.toString());
    }

    @Test
    void provenancePrintsCommitToolchainAndInputsWhenVerbose() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.disabled(), false, true);

        output.provenance(provenance(git(), Optional.of("sha256:inputs")));

        assertEquals(
                """
                  provenance
                    commit    abcdef0123456789 (abcdef012345) on main
                    built     2026-07-02T01:02:03Z
                    toolchain zolt 0.1.0-test · JDK 21.0.2 (Eclipse Adoptium)
                    inputs    sha256:inputs (resolution fingerprint)
                """,
                buffer.toString());
    }

    @Test
    void provenanceOmitsGitAndInputsWhenUnavailable() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.disabled(), false, true);

        output.provenance(provenance(GitProvenance.none(), Optional.empty()));

        assertEquals(
                """
                  provenance
                    built     2026-07-02T01:02:03Z
                    toolchain zolt 0.1.0-test · JDK 21.0.2 (Eclipse Adoptium)
                """,
                buffer.toString());
        assertFalse(buffer.toString().contains("commit"));
        assertFalse(buffer.toString().contains("inputs"));
    }

    @Test
    void provenanceColorsLabelsAndCopyableValuesWhenStyleIsEnabled() {
        StringWriter buffer = new StringWriter();
        CommandHumanOutput output =
                CommandHumanOutput.forTesting(new PrintWriter(buffer), ConsoleStyle.enabled(), false, true);

        output.provenance(provenance(git(), Optional.of("sha256:inputs")));

        assertEquals(
                "  \u001B[2mprovenance\u001B[0m\n"
                        + "    \u001B[2mcommit\u001B[0m    \u001B[36mabcdef0123456789 (abcdef012345) on main\u001B[0m\n"
                        + "    \u001B[2mbuilt\u001B[0m     2026-07-02T01:02:03Z\n"
                        + "    \u001B[2mtoolchain\u001B[0m zolt 0.1.0-test · JDK 21.0.2 (Eclipse Adoptium)\n"
                        + "    \u001B[2minputs\u001B[0m    \u001B[36msha256:inputs\u001B[0m (resolution fingerprint)\n",
                buffer.toString());
    }

    private static BuildProvenance provenance(GitProvenance git, Optional<String> resolutionFingerprint) {
        return new BuildProvenance(
                git,
                Instant.parse("2026-07-02T01:02:03Z"),
                "0.1.0-test",
                "21.0.2",
                "Eclipse Adoptium",
                resolutionFingerprint);
    }

    private static GitProvenance git() {
        return new GitProvenance(
                Optional.of("abcdef0123456789"),
                Optional.of("abcdef012345"),
                Optional.of("main"),
                false,
                Optional.empty());
    }
}
