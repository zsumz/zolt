package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertEquals(1, result.resourceCount());
        assertEquals("fixture\n", Files.readString(projectDir.resolve("target/test-classes/fixtures/input.txt")));
    }

    @Test
    void skipsUnchangedResourcesOnRepeatedCopy() throws IOException {
        Path resource = resource("src/main/resources/application.properties", "name=demo\n");

        ResourceCopyResult first = copier.copyMainResources(projectDir, BuildSettings.defaults());
        ResourceCopyResult second = copier.copyMainResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(resource), first.copiedResources());
        assertTrue(first.skippedResources().isEmpty());
        assertTrue(second.copiedResources().isEmpty());
        assertEquals(List.of(resource), second.skippedResources());
        assertEquals(1, second.resourceCount());
    }

    @Test
    void copiesChangedResourcesOnRepeatedCopy() throws IOException {
        Path resource = resource("src/main/resources/application.properties", "name=demo\n");
        copier.copyMainResources(projectDir, BuildSettings.defaults());
        Files.writeString(resource, "name=changed\n");

        ResourceCopyResult result = copier.copyMainResources(projectDir, BuildSettings.defaults());

        assertEquals(List.of(resource), result.copiedResources());
        assertTrue(result.skippedResources().isEmpty());
        assertEquals("name=changed\n", Files.readString(projectDir.resolve("target/classes/application.properties")));
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

    @Test
    void filtersMainResourcesWithLiteralAndProjectTokens() throws IOException {
        Path resource = resource("src/main/resources/application.properties", """
                name=@projectName@
                version=@projectVersion@
                greeting=@greeting@
                """);
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of(
                        "projectName", ResourceTokenSettings.project("name"),
                        "projectVersion", ResourceTokenSettings.project("version"),
                        "greeting", ResourceTokenSettings.literal("hello")));

        ResourceCopyResult result = copier.copyMainResources(projectDir, config(filtering));

        assertEquals(List.of(resource), result.copiedResources());
        assertEquals("""
                name=demo
                version=0.1.0
                greeting=hello
                """, Files.readString(projectDir.resolve("target/classes/application.properties")));
    }

    @Test
    void skipsUnchangedFilteredResourcesOnRepeatedCopy() throws IOException {
        Path resource = resource("src/main/resources/application.properties", "name=@projectName@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of("projectName", ResourceTokenSettings.project("name")));

        ResourceCopyResult first = copier.copyMainResources(projectDir, config(filtering));
        ResourceCopyResult second = copier.copyMainResources(projectDir, config(filtering));

        assertEquals(List.of(resource), first.copiedResources());
        assertTrue(second.copiedResources().isEmpty());
        assertEquals(List.of(resource), second.skippedResources());
    }

    @Test
    void testResourceFilteringRequiresExplicitOptIn() throws IOException {
        resource("src/test/resources/application.properties", "name=@projectName@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of("projectName", ResourceTokenSettings.project("name")));

        copier.copyTestResources(projectDir, config(filtering));

        assertEquals(
                "name=@projectName@\n",
                Files.readString(projectDir.resolve("target/test-classes/application.properties")));
    }

    @Test
    void filtersTestResourcesWhenExplicitlyEnabled() throws IOException {
        resource("src/test/resources/application.properties", "name=@projectName@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                true,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of("projectName", ResourceTokenSettings.project("name")));

        copier.copyTestResources(projectDir, config(filtering));

        assertEquals(
                "name=demo\n",
                Files.readString(projectDir.resolve("target/test-classes/application.properties")));
    }

    @Test
    void missingFilteringTokenFailsClearlyByDefault() throws IOException {
        resource("src/main/resources/application.properties", "name=@missing@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of());

        ResourceCopyException exception = assertThrows(
                ResourceCopyException.class,
                () -> copier.copyMainResources(projectDir, config(filtering)));

        assertTrue(exception.getMessage().contains("contains token @missing@"));
        assertTrue(exception.getMessage().contains("[resources.tokens].missing is not defined"));
        assertTrue(exception.getMessage().contains("[resources.filtering].missing = \"keep\""));
    }

    @Test
    void missingFilteringTokenCanRemainUnchanged() throws IOException {
        resource("src/main/resources/application.properties", "name=@missing@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.KEEP,
                Map.of());

        copier.copyMainResources(projectDir, config(filtering));

        assertEquals(
                "name=@missing@\n",
                Files.readString(projectDir.resolve("target/classes/application.properties")));
    }

    @Test
    void selectedBinaryResourceFilteringFailsClearly() throws IOException {
        Path resource = projectDir.resolve("src/main/resources/logo.bin");
        Files.createDirectories(resource.getParent());
        Files.write(resource, new byte[] {1, 2, 0, 4});
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.bin"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of());

        ResourceCopyException exception = assertThrows(
                ResourceCopyException.class,
                () -> copier.copyMainResources(projectDir, config(filtering)));

        assertTrue(exception.getMessage().contains("logo.bin"));
        assertTrue(exception.getMessage().contains("appears to be binary"));
    }

    private Path resource(String path, String content) throws IOException {
        Path resource = projectDir.resolve(path);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content);
        return resource.normalize();
    }

    private static ProjectConfig config(ResourceFilteringSettings filtering) {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(filtering));
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
