package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class TestPatternMatrixDocumentationTest {
    @Test
    void matrixCoversM28TestingAreasAndWorkspaceLayout() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/test-pattern-matrix.md"));

        assertTrue(matrix.contains("The packaged CLI application lives in `apps/zolt`."));
        assertTrue(matrix.contains("Built-artifact self-host truth"));
        assertTrue(matrix.contains("Resolver domain invariants"));
        assertTrue(matrix.contains("Workspace and config conformance"));
        assertTrue(matrix.contains("CLI output contracts"));
        assertTrue(matrix.contains("Framework fixture gates"));
        assertTrue(matrix.contains("Publish dry-run and release verification"));
        assertTrue(matrix.contains("Performance and memory budgets"));
        assertTrue(matrix.contains("Unsupported behavior diagnostics"));
    }

    @Test
    void matrixLinksMissingCoverageToM28FollowUps() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/test-pattern-matrix.md"));

        assertTrue(matrix.contains("|  | Built-artifact self-host smoke gate. |"));
        assertTrue(matrix.contains("|  | Built CLI startup and build identity checks. |"));
        assertTrue(matrix.contains("|  | Resolver regression fixture matrix. |"));
        assertTrue(matrix.contains("|  | Resolver generated-graph and timeout gates. |"));
        assertTrue(matrix.contains("|  | CLI and machine-readable model contract fixtures. |"));
        assertTrue(matrix.contains("|  | Public API and exported surface compatibility checks. |"));
        assertTrue(matrix.contains("|  | Workspace, config, and cache context guardrails. |"));
        assertTrue(matrix.contains("|  | Framework and generated-source fixture gates. |"));
        assertTrue(matrix.contains("|  | Publish/release downstream consume and rollback smokes. |"));
        assertTrue(matrix.contains("|  | Startup, response-time, cache, and memory budgets. |"));
        assertTrue(matrix.contains("|  | Fixed-sleep guardrails for PR-safe tests. |"));
    }

    @Test
    void docsIndexLinksMatrix() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`test-pattern-matrix.md`"));
    }
}
