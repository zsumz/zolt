package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradlePropertiesInspectionTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void readsGroupAndVersionFromGradleProperties() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'properties-demo'\n");
        Files.writeString(tempDir.resolve("gradle.properties"), """
                group=com.acme
                version=1.2.3
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");

        GradleProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("com.acme", project.group().orElseThrow());
        assertEquals("1.2.3", project.version().orElseThrow());
    }

    @Test
    void buildFileCoordinatesWinOverGradleProperties() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'properties-demo'\n");
        Files.writeString(tempDir.resolve("gradle.properties"), """
                group=com.from.properties
                version=1.2.3
                """);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                group = 'com.from.build'
                version = '9.9.9'
                """);

        GradleProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("com.from.build", project.group().orElseThrow());
        assertEquals("9.9.9", project.version().orElseThrow());
    }

    @Test
    void projectGradlePropertiesWinOverRootGradleProperties() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), """
                rootProject.name = 'multi'
                include 'app'
                """);
        Files.writeString(tempDir.resolve("gradle.properties"), """
                group=com.root
                version=1.0.0
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        Path app = tempDir.resolve("app");
        Files.createDirectories(app);
        Files.writeString(app.resolve("gradle.properties"), """
                group=com.app
                version=2.0.0
                """);
        Files.writeString(app.resolve("build.gradle"), "plugins { id 'java' }\n");

        GradleProjectInspection project = inspector.inspect(tempDir).projects().stream()
                .filter(candidate -> candidate.path().equals(Path.of("app")))
                .findFirst()
                .orElseThrow();

        assertEquals("com.app", project.group().orElseThrow());
        assertEquals("2.0.0", project.version().orElseThrow());
    }
}
