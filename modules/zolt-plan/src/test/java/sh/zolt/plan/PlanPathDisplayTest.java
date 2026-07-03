package sh.zolt.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlanPathDisplayTest {
    @TempDir
    private Path tempDir;

    @Test
    void displaysNormalizedProjectRelativePathsWithForwardSlashes() {
        Path root = tempDir.resolve("project");
        Path messyRoot = root.resolve(".").resolve("..").resolve("project");
        Path path = root.resolve("src/main/java/../resources/application.yml");

        assertEquals("src/main/resources/application.yml", PlanPathDisplay.displayPath(messyRoot, path));
    }

    @Test
    void keepsAbsoluteDisplayForSiblingPathsWithSamePrefix() {
        Path root = tempDir.resolve("project");
        Path sibling = tempDir.resolve("project-other/file.txt");

        assertEquals(
                sibling.toAbsolutePath().normalize().toString(),
                PlanPathDisplay.displayPath(root, sibling));
    }

    @Test
    void displaysProjectRootItselfAsEmptyRelativePath() {
        Path root = tempDir.resolve("project");

        assertEquals("", PlanPathDisplay.displayPath(root, root.resolve(".")));
    }
}
