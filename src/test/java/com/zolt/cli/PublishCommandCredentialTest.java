package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishCommandCredentialTest {
    @TempDir
    private Path tempDir;

    @Test
    void publishDryRunRoutesSnapshotAndReportsMissingCredentialsAndArtifact() throws IOException {
        Path projectDir = tempDir.resolve("publish-dry-run-snapshot-blocked");
        writeProjectConfig(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml"))
                .replace("version = \"0.1.0\"", "version = \"0.1.0-SNAPSHOT\"") + """

                [publish]
                releaseRepository = "company-releases"
                snapshotRepository = "company-snapshots"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"

                [publish.repositories.company-snapshots]
                url = "https://repo.example.test/snapshots"
                credentials = "publish-creds"

                [repositoryCredentials.publish-creds]
                usernameEnv = "ZOLT_TEST_MISSING_PUBLISH_USERNAME"
                passwordEnv = "ZOLT_TEST_MISSING_PUBLISH_PASSWORD"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "publish",
                "--dry-run",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Version kind: snapshot"));
        assertTrue(result.stdout().contains("Target repository: company-snapshots"));
        assertTrue(result.stdout().contains("Status: blocked"));
        assertTrue(result.stdout().contains("missing credential environment variables ZOLT_TEST_MISSING_PUBLISH_USERNAME, ZOLT_TEST_MISSING_PUBLISH_PASSWORD"));
        assertTrue(result.stdout().contains("missing artifact: run `zolt package`"));
        assertFalse(result.stdout().contains("repo.example.test/snapshots@"));
        assertEquals("", result.stderr());
    }

    @Test
    void publishDryRunBlocksAndRedactsEmbeddedRepositoryCredentials() throws IOException {
        Path projectDir = tempDir.resolve("publish-dry-run-redacted-url");
        writeProjectConfig(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://publish-user:super-secret@repo.example.test/releases"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "publish",
                "--dry-run",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Target URL: https://***@repo.example.test/releases"));
        assertTrue(result.stdout().contains("Status: blocked"));
        assertTrue(result.stdout().contains("publish repository `company-releases` URL contains embedded credentials"));
        assertTrue(result.stdout().contains("Move credentials to [repositoryCredentials] environment references."));
        assertFalse(result.stdout().contains("publish-user"));
        assertFalse(result.stdout().contains("super-secret"));
        assertFalse(result.stderr().contains("publish-user"));
        assertFalse(result.stderr().contains("super-secret"));
    }

    @Test
    void publishDryRunRejectsSnapshotVersionWithoutSnapshotTarget() throws IOException {
        Path projectDir = tempDir.resolve("publish-dry-run-missing-snapshot-target");
        writeProjectConfig(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml"))
                .replace("version = \"0.1.0\"", "version = \"0.1.0-SNAPSHOT\"") + """

                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"
                """);

        CommandResult result = execute(
                "publish",
                "--dry-run",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Project version `0.1.0-SNAPSHOT` requires [publish].snapshotRepository"));
        assertEquals("", result.stdout());
    }

    private static void writeProjectConfig(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "https://repo.maven.apache.org/maven2"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
    }
}
