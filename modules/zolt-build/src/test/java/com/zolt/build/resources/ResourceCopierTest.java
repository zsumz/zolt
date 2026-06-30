package com.zolt.build.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResourceCopierTest extends ResourceCopierTestSupport {
    private final ResourceCopier copier = new ResourceCopier();

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
}
