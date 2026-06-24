package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class CacheContextMatrixDocumentationTest {
    @Test
    void cacheContextMatrixNamesCoreDimensionsAndOwners() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/cache-context-matrix.md"));

        assertTrue(matrix.contains("Dependency coordinate and version"));
        assertTrue(matrix.contains("Dependency scope and directness"));
        assertTrue(matrix.contains("Classifier and artifact extension"));
        assertTrue(matrix.contains("Repository URL and credential environment names"));
        assertTrue(matrix.contains("Workspace member paths"));
        assertTrue(matrix.contains("Package mode"));
        assertTrue(matrix.contains("Framework mode"));
        assertTrue(matrix.contains("Generated-source tool inputs and required flags"));
        assertTrue(matrix.contains("Java release input"));
        assertTrue(matrix.contains("Artifact checksums"));
        assertTrue(matrix.contains("OS/architecture and Native Image executable"));
        assertTrue(matrix.contains("Zolt version or schema version"));
    }

    @Test
    void docsIndexLinksCacheContextMatrix() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`cache-context-matrix.md`"));
    }
}
