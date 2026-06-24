package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExplainCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void explainHelpShowsMigrationAuditCommand() {
        CommandResult result = execute("explain", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Audit a Maven or Gradle project for future Zolt migration."));
        assertTrue(result.stdout().contains("--blockers"));
        assertTrue(result.stdout().contains("--format"));
        assertTrue(result.stdout().contains("--scorecard"));
        assertTrue(result.stdout().contains("--source"));
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
    }

    @Test
    void explainTextPlaceholderIsActionableWhenSourceIsUnknown() {
        CommandResult result = execute("explain", "--directory", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt explain is not implemented yet."));
        assertTrue(result.stdout().contains("audit Maven and Gradle project metadata statically"));
        assertTrue(result.stdout().contains("This command will not execute Maven or Gradle"));
        assertTrue(result.stdout().contains("Requested source: auto"));
        assertTrue(result.stdout().contains("Project root: " + tempDir.toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("followUps/-add-zolt-explain-command-scaffold.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainRejectsInvalidFormatClearly() {
        CommandResult result = execute("explain", "--format", "xml");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--format'"));
        assertTrue(result.stderr().contains("TEXT"));
        assertTrue(result.stderr().contains("JSON"));
    }

    @Test
    void explainRejectsInvalidSourceClearly() {
        CommandResult result = execute("explain", "--source", "ant");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--source'"));
        assertTrue(result.stderr().contains("AUTO"));
        assertTrue(result.stderr().contains("MAVEN"));
        assertTrue(result.stderr().contains("GRADLE"));
    }

    @Test
    void explainScaffoldDoesNotExecuteMavenOrGradleWrappers() throws IOException {
        Path projectDir = tempDir.resolve("legacy");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), """
                <project>
                  <artifactId>legacy</artifactId>
                </project>
                """);
        Path marker = projectDir.resolve("executed.txt");
        Path mvnw = projectDir.resolve("mvnw");
        Path gradlew = projectDir.resolve("gradlew");
        Files.writeString(mvnw, "#!/usr/bin/env sh\nprintf mvn > '" + marker + "'\n");
        Files.writeString(gradlew, "#!/usr/bin/env sh\nprintf gradle > '" + marker + "'\n");
        assertTrue(mvnw.toFile().setExecutable(true));
        assertTrue(gradlew.toFile().setExecutable(true));

        CommandResult maven = execute("explain", "--cwd", projectDir.toString(), "--source", "maven");
        CommandResult gradle = execute("explain", "--cwd", projectDir.toString(), "--source", "gradle");

        assertEquals(0, maven.exitCode());
        assertEquals(1, gradle.exitCode());
        assertFalse(Files.exists(marker));
    }
}
