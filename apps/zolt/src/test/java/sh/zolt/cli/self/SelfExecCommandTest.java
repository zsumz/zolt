package sh.zolt.cli.self;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfExecCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void execRunsInstalledVersionAndStripsOptionalLeadingZolt() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeExecZolt(installed.installRoot().resolve("versions/0.2.0/bin/zolt"), "0.2.0");

        CommandResult result = execute(
                "self",
                "exec",
                "--install-root", installed.installRoot().toString(),
                "--current-executable", installed.binLink().toString(),
                "0.2.0",
                "--",
                "zolt",
                "test",
                "--workspace");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("exec:test --workspace"));
    }

    @Test
    void execPropagatesChildExitCodeAndStderr() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeExecZolt(installed.installRoot().resolve("versions/0.2.0/bin/zolt"), "0.2.0");

        CommandResult result = execute(
                "self",
                "exec",
                "--install-root", installed.installRoot().toString(),
                "--current-executable", installed.binLink().toString(),
                "0.2.0",
                "--",
                "fail");

        assertEquals(7, result.exitCode());
        assertTrue(result.stderr().contains("child failed"));
    }

    private InstalledFixture install(String version) throws IOException {
        Path installRoot = tempDir.resolve("home/.zolt");
        Path executable = installRoot.resolve("versions").resolve(version).resolve("bin/zolt");
        writeExecZolt(executable, version);
        Path bin = installRoot.resolve("bin");
        Files.createDirectories(bin);
        Path binLink = bin.resolve("zolt");
        Files.deleteIfExists(binLink);
        Files.createSymbolicLink(binLink, Path.of("../versions", version, "bin", "zolt"));
        return new InstalledFixture(installRoot, binLink);
    }

    private static void writeExecZolt(Path executable, String version) throws IOException {
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "${1:-}" == "--version" ]]; then
                  printf '%%s\\n' "%s"
                  exit 0
                fi
                if [[ "${1:-}" == "fail" ]]; then
                  printf 'child failed\\n' >&2
                  exit 7
                fi
                printf 'exec:%%s\\n' "$*"
                """.formatted(version));
        executable.toFile().setExecutable(true);
    }

    private record InstalledFixture(Path installRoot, Path binLink) {
    }
}
