package sh.zolt.cli.packaging;

import sh.zolt.cli.CliTestSupport;


import static sh.zolt.cli.packaging.CheckPackageContentsCommandTestSupport.writeMainSource;
import static sh.zolt.cli.packaging.CheckPackageContentsCommandTestSupport.writeProjectConfig;
import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckPackageContentsEvidenceTest {
    @TempDir
    private Path tempDir;

    @Test
    void checkContextCiRequiresPackageArtifactWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-require-package-missing");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), CliTestSupport.memberConfig("check-context-ci-require-package-missing"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--require-package",
                "--check", "package-contents",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-contents target/check-context-ci-require-package-missing-0.1.0.jar CI context requires the configured package artifact, but it is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt package` before `zolt check --context ci --require-package`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsRequiredPackageArtifactWithFreshEvidence() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-require-package-ok");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--require-package",
                "--check", "package-contents",
                "--cwd", projectDir.toString());

        assertEquals(0, packageResult.exitCode());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-contents demo Package mode `thin` has 0 dependency dispositions."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageContentsReportsMissingEvidenceForExistingArchive() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-missing-evidence");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Files.delete(projectDir.resolve("target/demo-0.1.0.jar.zolt-package.json"));

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, packageResult.exitCode());
        assertEquals(1, result.exitCode());
        assertTrue(Files.exists(jarPath));
        assertTrue(result.stdout().contains("error package-contents target/demo-0.1.0.jar Package artifact exists, but package evidence manifest is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt package` to regenerate target/demo-0.1.0.jar.zolt-package.json."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageContentsReportsStalePackageEvidence() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-stale-evidence");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Files.writeString(jarPath, "tampered\n", StandardOpenOption.APPEND);

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, packageResult.exitCode());
        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-contents target/demo-0.1.0.jar.zolt-package.json Package evidence manifest is stale for `target/demo-0.1.0.jar`."));
        assertTrue(result.stdout().contains("next: Run `zolt package` to regenerate the artifact and evidence manifest."));
        assertEquals("", result.stderr());
    }
}
