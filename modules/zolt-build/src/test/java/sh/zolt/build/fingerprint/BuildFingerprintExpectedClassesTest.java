package sh.zolt.build.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildFingerprintExpectedClassesTest {
    @TempDir
    private Path projectDir;

    private final BuildFingerprintExpectedClasses expectedClasses = new BuildFingerprintExpectedClasses();

    @Test
    void mapsJavaAndGroovySourcesToClassFiles() {
        Path output = projectDir.resolve("target/test-classes");
        List<Path> files = expectedClasses.files(
                projectDir,
                List.of("src/test/java", "src/test/groovy"),
                List.of(
                        projectDir.resolve("src/test/java/com/example/ExampleTest.java"),
                        projectDir.resolve("src/test/groovy/com/example/ExampleSpec.groovy")),
                output);

        assertEquals(List.of(
                output.resolve("com/example/ExampleSpec.class"),
                output.resolve("com/example/ExampleTest.class")), files);
        assertEquals(List.of(
                "target/test-classes/com/example/ExampleSpec.class",
                "target/test-classes/com/example/ExampleTest.class"), expectedClasses.entries(
                        projectDir,
                        List.of("src/test/java", "src/test/groovy"),
                        List.of(
                                projectDir.resolve("src/test/java/com/example/ExampleTest.java"),
                                projectDir.resolve("src/test/groovy/com/example/ExampleSpec.groovy")),
                        output));
    }

    @Test
    void usesLongestMatchingSourceRootAndIgnoresNonSources() {
        Path output = projectDir.resolve("target/classes");

        List<Path> files = expectedClasses.files(
                projectDir,
                List.of("src/main", "src/main/java"),
                List.of(
                        projectDir.resolve("src/main/java/com/example/Main.java"),
                        projectDir.resolve("src/main/java/com/example/readme.txt"),
                        projectDir.resolve("generated/Outside.java")),
                output);

        assertEquals(List.of(output.resolve("com/example/Main.class")), files);
    }

    @Test
    void reportsWhetherExpectedClassFilesExist() throws IOException {
        Path output = projectDir.resolve("target/classes");
        Path expectedClass = output.resolve("com/example/Main.class");
        List<Path> sources = List.of(projectDir.resolve("src/main/java/com/example/Main.java"));

        assertFalse(expectedClasses.present(projectDir, List.of("src/main/java"), sources, output));

        Files.createDirectories(expectedClass.getParent());
        Files.writeString(expectedClass, "compiled");

        assertTrue(expectedClasses.present(projectDir, List.of("src/main/java"), sources, output));
    }
}
