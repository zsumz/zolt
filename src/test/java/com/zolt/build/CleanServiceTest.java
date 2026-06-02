package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        CleanResult result = cleanService.clean(projectDir, BuildSettings.defaults());

        assertEquals(1, result.deletedCount());
        assertEquals(projectDir.resolve("target"), result.deletedPaths().getFirst());
        assertFalse(Files.exists(projectDir.resolve("target")));
    }

    @Test
    void deletesCustomOutputDirectories() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");

        CleanResult result = cleanService.clean(
                projectDir,
                new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"));

        assertEquals(2, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("out-test/test")));
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

        assertTrue(exception.getMessage().contains("Refusing to clean output path"));
    }

    private void file(String path) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
