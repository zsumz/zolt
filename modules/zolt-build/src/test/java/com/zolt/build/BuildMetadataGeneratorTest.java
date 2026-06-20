package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildMetadataGeneratorTest {
    private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-f]{40}");

    @TempDir
    private Path projectDir;

    @Test
    void generatesGitPropertiesWhenRepositoryMetadataIsAvailable() throws Exception {
        assumeTrue(gitAvailable());
        git(projectDir, "init");
        git(projectDir, "config", "user.email", "zolt@example.test");
        git(projectDir, "config", "user.name", "Zolt Test");
        Files.writeString(projectDir.resolve("README.md"), "demo\n");
        git(projectDir, "add", "README.md");
        git(projectDir, "-c", "commit.gpgsign=false", "commit", "-m", "initial");
        Files.createDirectories(projectDir.resolve("target/classes"));
        Files.writeString(projectDir.resolve("target/classes/stale-output.txt"), "ignored\n");
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(false, true, true));

        BuildMetadataResult result = generator().generate(
                projectDir,
                config,
                projectDir.resolve("target/classes"));

        String properties = Files.readString(projectDir.resolve("target/classes/git.properties"));
        assertEquals(1, result.generatedCount());
        assertTrue(properties.contains("git.branch="));
        assertTrue(properties.contains("git.dirty=false\n"));
        assertTrue(COMMIT_ID.matcher(value(properties, "git.commit.id")).matches());
        assertEquals(12, value(properties, "git.commit.id.abbrev").length());
    }

    private static ProjectConfig configWithMetadata(BuildMetadataSettings metadataSettings) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        metadataSettings));
    }

    private static BuildMetadataGenerator generator() {
        return new BuildMetadataGenerator(
                Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneOffset.UTC),
                new BuildMetadataGenerator.GitMetadataReader());
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void git(Path projectDirectory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(projectDirectory.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static String value(String properties, String key) {
        return properties.lines()
                .filter(line -> line.startsWith(key + "="))
                .map(line -> line.substring(key.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
