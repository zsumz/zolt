package sh.zolt.cli.supplychain;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SbomCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void generatesDeterministicCycloneDxFromTheLock() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProject(projectDir);

        CommandResult first = execute("sbom", "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(0, first.exitCode(), first.stderr());
        assertTrue(first.stdout().contains("\"bomFormat\": \"CycloneDX\""), first.stdout());
        assertTrue(first.stdout().contains("pkg:maven/org.example/lib@1.0.0?type=jar"), first.stdout());
        assertTrue(first.stdout().contains(
                "\"content\": \"1111111111111111111111111111111111111111111111111111111111111111\""), first.stdout());

        CommandResult second = execute("sbom", "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(first.stdout(), second.stdout());
    }

    @Test
    void changingTheTimestampDoesNotChangeTheSerial() throws IOException {
        Path projectDir = tempDir.resolve("timestamped");
        writeProject(projectDir);

        String bare = execute("sbom", "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString()).stdout();
        String timestamped = execute("sbom", "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--timestamp", "2026-07-23T00:00:00Z").stdout();

        assertNotEquals(bare, timestamped);
        assertTrue(timestamped.contains("\"timestamp\": \"2026-07-23T00:00:00Z\""), timestamped);
        assertEquals(serial(bare), serial(timestamped));
    }

    @Test
    void missingLockfileIsActionable() throws IOException {
        Path projectDir = tempDir.resolve("no-lock");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo"));

        CommandResult result = execute("sbom", "--cwd", projectDir.toString());
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("No zolt.lock"), result.stderr());
        assertTrue(result.stderr().contains("zolt resolve"), result.stderr());
    }

    private static void writeProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1
                projectResolutionFingerprint = "sha256:cli-fixture"

                [[package]]
                id = "org.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/example/lib/1.0.0/lib-1.0.0.jar"
                jarSha256 = "1111111111111111111111111111111111111111111111111111111111111111"
                dependencies = []
                """);
    }

    private static String serial(String json) {
        return json.lines().filter(line -> line.contains("serialNumber")).findFirst().orElseThrow();
    }
}
