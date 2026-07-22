package sh.zolt.cli.command.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import sh.zolt.cli.command.insight.GradleProjectDiscovery.GradleProjects;
import sh.zolt.explain.verify.GradleProjectCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for Gradle project discovery. Discovery is pure static inspection of the build files, so
 * these run without Gradle installed.
 */
final class GradleProjectDiscoveryTest {
    private final GradleProjectDiscovery discovery = new GradleProjectDiscovery();

    @Test
    void discoversRootAndIncludedProjectsWithGradlePaths() {
        Optional<Path> multiproject = locateExample("gradle-multiproject");
        assumeTrue(multiproject.isPresent(), "examples/migration-explain/gradle-multiproject not found");

        GradleProjects projects = discovery.discover(multiproject.orElseThrow());

        assertTrue(projects.projectPaths().contains(":"), projects.projectPaths().toString());
        assertTrue(projects.projectPaths().contains(":app"), projects.projectPaths().toString());
        assertTrue(projects.projectPaths().contains(":lib"), projects.projectPaths().toString());
        assertEquals("gradle-multiproject", projects.byPath().get(":").artifact());
        assertEquals("app", projects.byPath().get(":app").artifact());
        assertEquals("lib", projects.byPath().get(":lib").artifact());
    }

    @Test
    void recoversGroupAndVersionFromAllprojectsBlock(@TempDir Path root) {
        write(root.resolve("settings.gradle"), "rootProject.name = 'demo'\ninclude 'app'\n");
        write(root.resolve("build.gradle"),
                "allprojects {\n  group = 'com.example'\n  version = '2.1.0'\n}\n");
        write(root.resolve("app/build.gradle"), "plugins { id 'java' }\n");

        GradleProjects projects = discovery.discover(root);

        GradleProjectCoordinates app = projects.byPath().get(":app");
        // The app project declares neither group nor version; both fall back to the root allprojects block.
        assertEquals("com.example:app", app.moduleKey());
        assertEquals("2.1.0", app.version());
    }

    private static Optional<Path> locateExample(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("examples/migration-explain/" + name);
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write fixture " + path, exception);
        }
    }
}
