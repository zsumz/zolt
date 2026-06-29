package com.zolt.build.packaging;

import static com.zolt.build.PackageServiceTestSupport.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.PackageServiceTestSupport;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.ProjectPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageArtifactPathPlannerTest {
    @TempDir
    private Path projectDir;

    private final PackageArtifactPathPlanner planner = new PackageArtifactPathPlanner();

    @Test
    void derivesJarPathFromOutputRootProjectNameAndVersion() {
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        ".zolt/build",
                        ".zolt/build/classes",
                        ".zolt/build/test-classes"));

        assertEquals(projectDir.resolve(".zolt/build/demo-0.1.0.jar"), planner.jarPath(projectDir, config));
    }

    @Test
    void derivesArchivePathForRequestedExtension() {
        assertEquals(
                projectDir.resolve("target/demo-0.1.0.war"),
                planner.archivePath(projectDir, config(Optional.of("com.example.Main")), "war"));
    }

    @Test
    void rejectsUnsafeArtifactBaseNamesWithProjectPathDiagnostics() {
        ProjectConfig config = config(new ProjectMetadata(
                "../demo",
                "0.1.0",
                "com.example",
                PackageServiceTestSupport.currentJavaMajorVersion(),
                Optional.of("com.example.Main")));

        ProjectPathException exception = assertThrows(ProjectPathException.class, () -> planner.jarPath(projectDir, config));

        assertTrue(exception.getMessage().contains("Invalid [project].name value `../demo`"));
        assertTrue(exception.getMessage().contains("cannot be used in derived file names"));
    }
}
