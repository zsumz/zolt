package sh.zolt.cli.insight;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExplainCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void explainTextPlaceholderIsActionableWhenSourceIsUnknown() {
        Path root = tempDir.toAbsolutePath().normalize();
        CommandResult result = execute("explain", "--directory", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("No Maven or Gradle metadata found at " + root + "."));
        assertTrue(result.stdout().contains("zolt explain audits Maven and Gradle metadata statically"));
        assertFalse(result.stdout().contains("not implemented yet"));
        assertTrue(result.stdout().contains("Requested source: auto"));
        assertTrue(result.stdout().contains("Project root: " + root));
        assertTrue(result.stdout().contains("Next: Run zolt explain against a Maven (pom.xml) or Gradle "
                + "(settings.gradle[.kts]/build.gradle[.kts]) project, or pass --directory <path> "
                + "or --source maven|gradle to point it at one."));
        assertEquals("", result.stderr());
    }

    @Test
    void explainPlaceholderUsesModernHumanOutputControls() {
        Path root = tempDir.toAbsolutePath().normalize();
        CommandResult color = execute("--color=always", "explain", "--directory", tempDir.toString());
        CommandResult quiet = execute("--quiet", "explain", "--directory", tempDir.toString());
        CommandResult json = execute(
                "--color=always",
                "explain",
                "--directory", tempDir.toString(),
                "--format", "json");

        assertEquals(1, color.exitCode());
        assertTrue(color.stdout().contains("\u001B[36mNo\u001B[0m Maven or Gradle metadata found at " + root + "."));
        assertTrue(color.stdout().contains("Requested source: auto"));
        assertTrue(color.stdout().contains("Next: Run zolt explain against a Maven (pom.xml) or Gradle"));
        assertFalse(color.stdout().contains("not implemented yet"));
        assertFalse(color.stdout().contains("\u001B[36mNext"));
        assertEquals("", color.stderr());
        assertEquals(1, quiet.exitCode());
        assertEquals("", quiet.stdout());
        assertEquals("", quiet.stderr());
        assertEquals(1, json.exitCode());
        assertFalse(json.stdout().contains("\u001B["));
        assertFalse(json.stdout().contains("\"status\":\"not-implemented\""));
        assertTrue(json.stdout().contains("\"status\":\"no-project\""));
        assertTrue(json.stdout().contains("\"command\":\"explain\""));
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
