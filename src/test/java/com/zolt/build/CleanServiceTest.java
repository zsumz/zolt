package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CleanServiceTest {
    private final CleanService cleanService = new CleanService();

    @TempDir
    private Path projectDir;

    @Test
    void deletesDefaultTargetDirectory() throws IOException {
        file("target/classes/com/example/Main.class");
        file("target/test-classes/com/example/MainTest.class");
        file("target/generated/sources/annotations/com/example/Generated.java");

        CleanResult result = cleanService.clean(projectDir, BuildSettings.defaults());

        assertEquals(1, result.deletedCount());
        assertEquals(projectDir.resolve("target"), result.deletedPaths().getFirst());
        assertFalse(Files.exists(projectDir.resolve("target")));
    }

    @Test
    void deletesCustomOutputDirectories() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");
        file("target/generated/sources/annotations/com/example/Generated.java");
        file("target/generated/test-sources/annotations/com/example/GeneratedTest.java");

        CleanResult result = cleanService.clean(
                projectDir,
                new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"),
                CompilerSettings.defaults());

        assertEquals(4, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("out-test/test")));
        assertFalse(Files.exists(projectDir.resolve("target/generated/sources/annotations")));
        assertFalse(Files.exists(projectDir.resolve("target/generated/test-sources/annotations")));
    }

    @Test
    void deletesConfiguredGeneratedSourceDirectories() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");
        file("build/generated/main/com/example/Generated.java");
        file("build/generated/test/com/example/GeneratedTest.java");

        CleanResult result = cleanService.clean(
                projectDir,
                new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"),
                new CompilerSettings("build/generated/main", "build/generated/test"));

        assertEquals(4, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("build/generated/main")));
        assertFalse(Files.exists(projectDir.resolve("build/generated/test")));
    }

    @Test
    void deletesQuarkusOutputsWhenFrameworkIsEnabled() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");
        file("target/quarkus/zolt-augmentation.properties");
        file("target/quarkus-app/quarkus-run.jar");

        CleanResult result = cleanService.clean(
                projectDir,
                config(new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"), true));

        assertEquals(4, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("out-test/test")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus-app")));
    }

    @Test
    void leavesQuarkusOutputsWhenFrameworkIsDisabled() throws IOException {
        file("out/main/Main.class");
        file("target/quarkus/zolt-augmentation.properties");
        file("target/quarkus-app/quarkus-run.jar");

        CleanResult result = cleanService.clean(
                projectDir,
                config(new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"), false));

        assertEquals(1, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/zolt-augmentation.properties")));
        assertTrue(Files.exists(projectDir.resolve("target/quarkus-app/quarkus-run.jar")));
    }

    @Test
    void missingOutputDirectoriesAreCleanNoOp() {
        CleanResult result = cleanService.clean(projectDir, BuildSettings.defaults());

        assertEquals(0, result.deletedCount());
    }

    @Test
    void doesNotDeleteGlobalDependencyCache() throws IOException {
        file(".zolt/cache/com/example/app.jar");
        file("target/classes/com/example/Main.class");

        cleanService.clean(projectDir, BuildSettings.defaults());

        assertTrue(Files.exists(projectDir.resolve(".zolt/cache/com/example/app.jar")));
    }

    @Test
    void refusesOutputPathOutsideProject() {
        CleanException exception = assertThrows(
                CleanException.class,
                () -> cleanService.clean(
                        projectDir,
                        new BuildSettings("src/main/java", "src/test/java", "../outside", "target/test-classes")));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("../outside"));
    }

    @Test
    void refusesWindowsStyleOutputPath() {
        CleanException exception = assertThrows(
                CleanException.class,
                () -> cleanService.clean(
                        projectDir,
                        new BuildSettings("src/main/java", "src/test/java", "C:\\outside\\classes", "target/test-classes")));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("C:\\outside\\classes"));
    }

    @Test
    void refusesOutputSymlinkThatEscapesProject() throws IOException {
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-clean-");
        createSymlink(projectDir.resolve("target/classes"), outside);

        CleanException exception = assertThrows(
                CleanException.class,
                () -> cleanService.clean(projectDir, BuildSettings.defaults()));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertTrue(Files.exists(outside));
    }

    @Test
    void refusesOutputWithSymlinkedParentEvenWhenOutputIsMissing() throws IOException {
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-clean-parent-");
        createSymlink(projectDir.resolve("target"), outside);

        CleanException exception = assertThrows(
                CleanException.class,
                () -> cleanService.clean(projectDir, BuildSettings.defaults()));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("target/classes"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertTrue(Files.exists(outside));
    }

    @Test
    void preservesDeclaredGeneratedRootsByDefault() throws IOException {
        file("target/classes/com/example/Main.class");
        file("target/generated/sources/openapi/com/example/Generated.java");
        BuildSettings settings = BuildSettings.defaults().withGeneratedSources(
                List.of(new GeneratedSourceStep(
                        "openapi",
                        GeneratedSourceKind.DECLARED_ROOT,
                        "java",
                        "target/generated/sources/openapi",
                        List.of("src/main/openapi/api.yaml"),
                        true,
                        false)),
                List.of());

        CleanResult result = cleanService.clean(projectDir, settings);

        assertEquals(1, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("target/classes")));
        assertTrue(Files.exists(projectDir.resolve("target/generated/sources/openapi/com/example/Generated.java")));
    }

    @Test
    void deletesDeclaredGeneratedRootsWhenCleanIsEnabled() throws IOException {
        file("target/classes/com/example/Main.class");
        file("target/generated/sources/openapi/com/example/Generated.java");
        BuildSettings settings = BuildSettings.defaults().withGeneratedSources(
                List.of(new GeneratedSourceStep(
                        "openapi",
                        GeneratedSourceKind.DECLARED_ROOT,
                        "java",
                        "target/generated/sources/openapi",
                        List.of("src/main/openapi/api.yaml"),
                        true,
                        true)),
                List.of());

        CleanResult result = cleanService.clean(projectDir, settings);

        assertEquals(1, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("target")));
    }

    private void file(String path) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }

    private static ProjectConfig config(BuildSettings buildSettings, boolean quarkusEnabled) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                buildSettings)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(quarkusEnabled, QuarkusPackageMode.FAST_JAR)));
    }
}
