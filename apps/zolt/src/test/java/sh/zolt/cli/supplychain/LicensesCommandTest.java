package sh.zolt.cli.supplychain;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LicensesCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void groupsResolvedLicensesAndWritesNotices() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cache = tempDir.resolve("cache");
        writeProject(projectDir);
        writePom(cache, "Apache License, Version 2.0");

        CommandResult text = execute("licenses", "--cwd", projectDir.toString(),
                "--cache-root", cache.toString());
        assertEquals(0, text.exitCode(), text.stderr());
        assertTrue(text.stdout().contains("Apache-2.0 (1)"), text.stdout());
        assertTrue(text.stdout().contains("org.example:lib:1.0.0"), text.stdout());

        Path noticesFile = projectDir.resolve("THIRD_PARTY.txt");
        CommandResult json = execute("licenses", "--cwd", projectDir.toString(),
                "--cache-root", cache.toString(),
                "--format", "json",
                "--notices", noticesFile.toString());
        assertEquals(0, json.exitCode(), json.stderr());
        assertTrue(json.stdout().contains("\"license\": \"Apache-2.0\""), json.stdout());
        assertTrue(json.stdout().contains("\"status\": \"spdx\""), json.stdout());
        assertTrue(Files.readString(noticesFile).contains("org.example:lib:1.0.0"), "notices lists the dependency");
    }

    private static void writeProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1
                projectResolutionFingerprint = "sha256:cli-licenses"

                [[package]]
                id = "org.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/example/lib/1.0.0/lib-1.0.0.jar"
                pom = "org/example/lib/1.0.0/lib-1.0.0.pom"
                jarSha256 = "1111111111111111111111111111111111111111111111111111111111111111"
                dependencies = []
                """);
    }

    private static void writePom(Path cache, String licenseName) throws IOException {
        Path pom = cache.resolve("org/example/lib/1.0.0/lib-1.0.0.pom");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                  <licenses><license><name>%s</name></license></licenses>
                </project>
                """.formatted(licenseName));
    }
}
