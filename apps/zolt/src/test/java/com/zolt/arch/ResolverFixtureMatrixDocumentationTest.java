package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class ResolverFixtureMatrixDocumentationTest {
    @Test
    void resolverFixtureMatrixNamesCoreFixtureGroupsAndUpdateRules() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/resolver-fixture-matrix.md"));

        assertTrue(matrix.contains("Scope filtering and transitive scope selection"));
        assertTrue(matrix.contains("Classifiers"));
        assertTrue(matrix.contains("Managed versions"));
        assertTrue(matrix.contains("Exclusions"));
        assertTrue(matrix.contains("Optional dependencies"));
        assertTrue(matrix.contains("Parents and BOMs"));
        assertTrue(matrix.contains("Relocation"));
        assertTrue(matrix.contains("Repository metadata and cache integrity"));
        assertTrue(matrix.contains("Generated Graph And Timeout Gates"));
        assertTrue(matrix.contains("DependencyGraphTraverserGeneratedGraphTest"));
        assertTrue(matrix.contains("Update golden output only with an implementation followUp or explicit reviewer note."));
    }

    @Test
    void docsIndexLinksResolverFixtureMatrix() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`resolver-fixture-matrix.md`"));
    }
}
