package com.zolt.arch;

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
                package com.zolt.alpha;

                import com.zolt.beta.Beta;
                import static com.zolt.gamma.Gamma.value;

                final class A {}
                """);
        write(
                tempDir.resolve("b/B.java"),
                """
                package com.zolt.beta.internal;

                import com.zolt.beta.Sibling;
                import java.util.List;

                final class B {}
                """);

        PackageGraph graph = PackageGraph.scan(tempDir);

        assertEquals(
                Set.of(
                        new PackageEdge("com.zolt.alpha", "com.zolt.beta"),
                        new PackageEdge("com.zolt.alpha", "com.zolt.gamma")),
                graph.edges());
    }

    @Test
    void cyclePathIsCompactAndReadable() {
        PackageGraph graph = new PackageGraph(Map.of(
                "com.zolt.a", Set.of("com.zolt.b"),
                "com.zolt.b", Set.of("com.zolt.c"),
                "com.zolt.c", Set.of("com.zolt.a")));

        assertEquals(
                "com.zolt.a -> com.zolt.b -> com.zolt.c -> com.zolt.a",
                graph.cyclePath(Set.of("com.zolt.a", "com.zolt.b", "com.zolt.c")));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
