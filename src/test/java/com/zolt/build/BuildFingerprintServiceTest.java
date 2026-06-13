package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathSet;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildFingerprintServiceTest {
    private final BuildFingerprintService service = new BuildFingerprintService();

    @TempDir
    private Path projectDir;

    @Test
    void rejectsUnsafeMainResourceRootBeforeFingerprinting() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        ProjectConfig config = config().withBuildSettings(new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                List.of(),
                List.of("../outside-resources"),
                List.of("src/test/resources"),
                null));

        BuildException exception = assertThrows(
                BuildException.class,
                () -> service.writeMainCompileFingerprint(
                        projectDir,
                        config,
                        projectDir.resolve("zolt.lock"),
                        new SourceDiscoveryResult(List.of(), List.of()),
                        emptyClasspaths(),
                        projectDir.resolve("target/classes"),
                        projectDir.resolve("target/generated/sources/annotations")));

        assertTrue(exception.getMessage().contains("[resources].main"));
        assertTrue(exception.getMessage().contains("../outside-resources"));
    }

    @Test
    void rejectsUnsafeGeneratedSourceInputBeforeFingerprinting() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        ProjectConfig config = config().withBuildSettings(config().build().withGeneratedSources(
                List.of(new GeneratedSourceStep(
                        "openapi",
                        GeneratedSourceKind.DECLARED_ROOT,
                        "java",
                        "target/generated/sources/openapi",
                        List.of("../api.yaml"),
                        true,
                        false)),
                List.of()));

        BuildException exception = assertThrows(
                BuildException.class,
                () -> service.writeMainCompileFingerprint(
                        projectDir,
                        config,
                        projectDir.resolve("zolt.lock"),
                        new SourceDiscoveryResult(List.of(), List.of()),
                        emptyClasspaths(),
                        projectDir.resolve("target/classes"),
                        projectDir.resolve("target/generated/sources/annotations")));

        assertTrue(exception.getMessage().contains("[generated.openapi].inputs"));
        assertTrue(exception.getMessage().contains("../api.yaml"));
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private static ClasspathSet emptyClasspaths() {
        Classpath empty = new Classpath(List.of());
        return new ClasspathSet(empty, empty, empty, empty, empty, empty);
    }
}
