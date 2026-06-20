package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageEvidenceManifestValidationTest {
    @TempDir
    private Path projectDir;

    @Test
    void packageEvidenceRejectsUnsafeResourceRoot() throws IOException {
        Path classes = projectDir.resolve("target/classes");
        Path archive = projectDir.resolve("target/demo-0.1.0.jar");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "archive");
        ProjectConfig config = PackageServiceTestSupport.config(Optional.of("com.example.Main"))
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of(),
                        List.of("../outside-resources"),
                        List.of("src/test/resources"),
                        null));
        BuildResult buildResult = new BuildResult(Optional.empty(), 0, 0, classes, "");
        PackagePlan plan = new PackagePlan(
                projectDir,
                PackageMode.THIN,
                archive,
                classes,
                "classes-root",
                Optional.empty(),
                List.of(),
                List.of());
        PackageResult result = new PackageResult(
                buildResult,
                PackageMode.THIN,
                archive,
                Optional.empty(),
                Optional.empty(),
                0,
                false,
                List.of());

        PackageException exception = assertThrows(
                PackageException.class,
                () -> new PackageEvidenceManifestWriter().write(projectDir, config, plan, result, List.of()));

        assertTrue(exception.getMessage().contains("[resources].main"), exception.getMessage());
        assertTrue(exception.getMessage().contains("../outside-resources"), exception.getMessage());
    }
}
