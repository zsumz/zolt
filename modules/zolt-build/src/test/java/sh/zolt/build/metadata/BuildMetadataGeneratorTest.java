package sh.zolt.build.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.project.BuildMetadataSettings;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.provenance.BuildProvenance;
import sh.zolt.provenance.GitProvenance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildMetadataGeneratorTest {
    private static final String COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant FIXED_TIME = Instant.parse("2026-06-08T00:00:00Z");

    @TempDir
    private Path projectDir;

    @Test
    void generatesGitPropertiesWhenRepositoryMetadataIsAvailable() throws Exception {
        writeGitMetadata(projectDir, COMMIT_SHA);
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(false, true, true));

        BuildMetadataResult result = generator(Map.of()).generate(
                projectDir,
                config,
                projectDir.resolve("target/classes"));

        String properties = Files.readString(projectDir.resolve("target/classes/git.properties"));
        assertEquals(1, result.generatedCount());
        assertEquals("""
                git.branch=main
                git.commit.id=0123456789abcdef0123456789abcdef01234567
                git.commit.id.abbrev=0123456789ab
                """, properties);
    }

    @Test
    void reproducibleBuildInfoUsesSourceDateEpochWhenPresent() throws IOException {
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(true, false, true));

        BuildMetadataResult result = generator(Map.of("SOURCE_DATE_EPOCH", "1700000000")).generate(
                projectDir,
                config,
                projectDir.resolve("target/classes"));

        assertEquals(1, result.generatedCount());
        assertEquals("2023-11-14T22:13:20Z", value(
                Files.readString(projectDir.resolve("target/classes/META-INF/build-info.properties")),
                "build.time"));
    }

    @Test
    void reproducibleBuildInfoFallsBackToEpochWhenSourceDateEpochIsMissing() throws IOException {
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(true, false, true));

        generator(Map.of()).generate(
                projectDir,
                config,
                projectDir.resolve("target/classes"));

        assertEquals("1970-01-01T00:00:00Z", value(
                Files.readString(projectDir.resolve("target/classes/META-INF/build-info.properties")),
                "build.time"));
    }

    @Test
    void nonReproducibleBuildInfoUsesClockEvenWhenSourceDateEpochIsPresent() throws IOException {
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(true, false, false));

        generator(Map.of("SOURCE_DATE_EPOCH", "1700000000")).generate(
                projectDir,
                config,
                projectDir.resolve("target/classes"));

        assertEquals("2026-06-08T00:00:00Z", value(
                Files.readString(projectDir.resolve("target/classes/META-INF/build-info.properties")),
                "build.time"));
    }

    @Test
    void buildInfoIncludesZoltBuilderMetadataWhenSupplied() throws IOException {
        ProjectConfig config = configWithMetadata(new BuildMetadataSettings(true, false, true));
        BuildMetadataGenerator generator = new BuildMetadataGenerator(
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC),
                (projectRoot, environment, clock) -> provenance(clock.instant()),
                Map.of());

        generator.generate(projectDir, config, projectDir.resolve("target/classes"));

        String properties = Files.readString(projectDir.resolve("target/classes/META-INF/build-info.properties"));
        assertEquals("zolt", value(properties, "build.tool.name"));
        assertEquals("0.1.0-zap.20260707.abcdef123456", value(properties, "build.tool.version"));
        assertEquals("sha256:build-info-inputs", value(properties, "build.resolution.fingerprint"));
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

    private static BuildMetadataGenerator generator(Map<String, String> environment) {
        return new BuildMetadataGenerator(
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC),
                environment);
    }

    private static BuildProvenance provenance(Instant instant) {
        return new BuildProvenance(
                new GitProvenance(
                        Optional.of(COMMIT_SHA),
                        Optional.of("0123456789ab"),
                        Optional.of("main"),
                        false,
                        Optional.empty()),
                instant,
                "0.1.0-zap.20260707.abcdef123456",
                "21.0.2",
                "Eclipse Adoptium",
                Optional.of("sha256:build-info-inputs"));
    }

    private static void writeGitMetadata(Path projectDir, String sha) throws IOException {
        Path head = projectDir.resolve(".git/HEAD");
        Path branch = projectDir.resolve(".git/refs/heads/main");
        Files.createDirectories(branch.getParent());
        Files.writeString(head, "ref: refs/heads/main\n");
        Files.writeString(branch, sha + "\n");
    }

    private static String value(String properties, String key) {
        return properties.lines()
                .filter(line -> line.startsWith(key + "="))
                .map(line -> line.substring(key.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
