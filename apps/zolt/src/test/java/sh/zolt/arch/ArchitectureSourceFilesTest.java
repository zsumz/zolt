package sh.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureSourceFilesTest {
    @Test
    void javaFilesSkipsMissingRootsAndReturnsSortedJavaFiles(@TempDir Path tempDir) throws IOException {
        Path alpha = tempDir.resolve("alpha/src/main/java/com/example/Alpha.java");
        Path beta = tempDir.resolve("beta/src/main/java/com/example/Beta.java");
        Path readme = tempDir.resolve("alpha/src/main/java/com/example/README.md");
        Path directoryWithJavaSuffix = tempDir.resolve("alpha/src/main/java/com/example/Nested.java");
        write(beta, "final class Beta {}\n");
        write(readme, "not java\n");
        write(alpha, "final class Alpha {}\n");
        Files.createDirectories(directoryWithJavaSuffix);

        assertEquals(
                List.of(alpha, beta),
                ArchitectureSourceFiles.javaFiles(List.of(
                        tempDir.resolve("missing/src/main/java"),
                        tempDir.resolve("beta/src/main/java"),
                        tempDir.resolve("alpha/src/main/java"))));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
