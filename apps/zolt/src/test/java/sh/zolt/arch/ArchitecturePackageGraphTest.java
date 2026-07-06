package sh.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitecturePackageGraphTest {
    @Test
    void scannerBuildsPackageEdgesFromJavaImports(@TempDir Path tempDir) throws IOException {
        write(
                tempDir.resolve("a/A.java"),
                """
                package sh.zolt.alpha;

                import sh.zolt.beta.Beta;
                import static sh.zolt.gamma.Gamma.value;

                final class A {}
                """);
        write(
                tempDir.resolve("b/B.java"),
                """
                package sh.zolt.beta.internal;

                import sh.zolt.beta.Sibling;
                import java.util.List;

                final class B {}
                """);

        PackageGraph graph = PackageGraph.scan(tempDir);

        assertEquals(
                Set.of(
                        new PackageEdge("sh.zolt.alpha", "sh.zolt.beta"),
                        new PackageEdge("sh.zolt.alpha", "sh.zolt.gamma")),
                graph.edges());
    }

    @Test
    void cyclePathIsCompactAndReadable() {
        PackageGraph graph = new PackageGraph(Map.of(
                "sh.zolt.a", Set.of("sh.zolt.b"),
                "sh.zolt.b", Set.of("sh.zolt.c"),
                "sh.zolt.c", Set.of("sh.zolt.a")));

        assertEquals(
                "sh.zolt.a -> sh.zolt.b -> sh.zolt.c -> sh.zolt.a",
                graph.cyclePath(Set.of("sh.zolt.a", "sh.zolt.b", "sh.zolt.c")));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
