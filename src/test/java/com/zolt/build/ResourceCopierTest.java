package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourceCopierTest {
    private final ResourceCopier copier = new ResourceCopier();

    @TempDir
    private Path projectDir;

    @Test
    void copiesMainResourcesDeterministicallyPreservingRelativePaths() throws IOException {
        Path alpha = resource("src/main/resources/com/example/alpha.properties", "alpha=true\n");
        Path zeta = resource("src/main/resources/zeta.txt", "zeta\n");

        ResourceCopyResult result = copier.copyMainResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(alpha, zeta), result.copiedResources());
        assertEquals(2, result.copiedCount());
        assertEquals("alpha=true\n", Files.readString(projectDir.resolve("target/classes/com/example/alpha.properties")));
        assertEquals("zeta\n", Files.readString(projectDir.resolve("target/classes/zeta.txt")));
    }

    @Test
    void copiesTestResourcesToTestOutput() throws IOException {
        Path fixture = resource("src/test/resources/fixtures/input.txt", "fixture\n");

        ResourceCopyResult result = copier.copyTestResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(fixture), result.copiedResources());
        assertEquals("fixture\n", Files.readString(projectDir.resolve("target/test-classes/fixtures/input.txt")));
    }

    @Test
    void copiesConfiguredGeneratedResourceRoots() throws IOException {
        Path app = resource("src/main/resources/application.properties", "name=demo\n");
        Path generatedCss = resource("target/generated/resources/static/app.css", "body {}\n");
        BuildSettings settings = buildSettingsWithResourceRoots(
                List.of("src/main/resources", "target/generated/resources"),
                List.of("src/test/resources", "target/generated/test-resources"));

        ResourceCopyResult main = copier.copyMainResources(projectDir, settings);

        assertEquals(List.of(app, generatedCss), main.copiedResources());
        assertEquals("name=demo\n", Files.readString(projectDir.resolve("target/classes/application.properties")));
        assertEquals("body {}\n", Files.readString(projectDir.resolve("target/classes/static/app.css")));
    }

    @Test
    void duplicateConfiguredResourceOutputPathsFailClearly() throws IOException {
        resource("src/main/resources/application.properties", "name=source\n");
        resource("target/generated/resources/application.properties", "name=generated\n");
        BuildSettings settings = buildSettingsWithResourceRoots(
                List.of("src/main/resources", "target/generated/resources"),
                List.of("src/test/resources"));

        ResourceCopyException exception = assertThrows(
                ResourceCopyException.class,
                () -> copier.copyMainResources(projectDir, settings));

        assertEquals(
                "Duplicate resource path `application.properties` from configured resource roots. Remove one copy or choose a distinct output path.",
                exception.getMessage());
    }

    @Test
    void absoluteConfiguredResourceRootsFailClearly() {
        BuildSettings settings = buildSettingsWithResourceRoots(
                List.of(projectDir.resolve("outside").toString()),
                List.of("src/test/resources"));

        ResourceCopyException exception = assertThrows(
                ResourceCopyException.class,
                () -> copier.copyMainResources(projectDir, settings));

        assertTrue(exception.getMessage().contains("Invalid resource root `"));
        assertTrue(exception.getMessage().contains("Use a project-relative path under the project directory."));
    }

    @Test
    void missingResourceDirectoriesReturnEmptyResults() {
        ResourceCopyResult main = copier.copyMainResources(projectDir, BuildSettings.defaults());
        ResourceCopyResult test = copier.copyTestResources(projectDir, BuildSettings.defaults());

        assertTrue(main.copiedResources().isEmpty());
        assertTrue(test.copiedResources().isEmpty());
    }

    @Test
    void skipsJavaFilesAndBuildOutputSegments() throws IOException {
        Path config = resource("src/main/resources/config/app.properties", "ok=true\n");
        resource("src/main/resources/com/example/NotAResource.java", "final class NotAResource {}\n");
        resource("src/main/resources/target/generated.txt", "target\n");
        resource("src/main/resources/build/generated.txt", "build\n");

        ResourceCopyResult result = copier.copyMainResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(config), result.copiedResources());
        assertTrue(Files.exists(projectDir.resolve("target/classes/config/app.properties")));
        assertFalse(Files.exists(projectDir.resolve("target/classes/com/example/NotAResource.java")));
        assertFalse(Files.exists(projectDir.resolve("target/classes/target/generated.txt")));
        assertFalse(Files.exists(projectDir.resolve("target/classes/build/generated.txt")));
    }

    @Test
    void doesNotIgnoreNestedResourceDirectoryNamedBuild() throws IOException {
        Path resource = resource("src/main/resources/com/example/build/info.txt", "ok\n");

        ResourceCopyResult result = copier.copyMainResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(resource), result.copiedResources());
        assertEquals("ok\n", Files.readString(projectDir.resolve("target/classes/com/example/build/info.txt")));
    }

    private Path resource(String path, String content) throws IOException {
        Path resource = projectDir.resolve(path);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content);
        return resource.normalize();
    }

    private static BuildSettings buildSettingsWithResourceRoots(
            List<String> resourceRoots,
            List<String> testResourceRoots) {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                resourceRoots,
                testResourceRoots,
                BuildMetadataSettings.defaults());
    }
}
