package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class PerformanceBudgetMatrixDocumentationTest {
    @Test
    void performanceBudgetMatrixNamesTimingMemoryAndContextLanes() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/performance-budget-matrix.md"));

        assertTrue(matrix.contains("`scripts/perf-smoke-ci`"));
        assertTrue(matrix.contains("`scripts/perf-smoke-ci-budgets.json`"));
        assertTrue(matrix.contains("`scripts/perf-cold-resolve-gate`"));
        assertTrue(matrix.contains("`scripts/perf-native-compare`"));
        assertTrue(matrix.contains("`scripts/perf-smoke --scenario large-workspace`"));
        assertTrue(matrix.contains("`scripts/perf-memory-budget`"));
        assertTrue(matrix.contains("`scripts/perf-large-source-compare`"));
        assertTrue(matrix.contains("`scripts/perf-netty-compare`"));
        assertTrue(matrix.contains("`scripts/report-context-footprint`"));
        assertTrue(matrix.contains("`scripts/report-file-size-budgets`"));
        assertTrue(matrix.contains("`maxRssKiB`"));
        assertTrue(matrix.contains("Cold-cache and warm-cache rows must stay separate"));
        assertTrue(matrix.contains("scenario, mode, command, phase, observed value, and budget"));
    }

    @Test
    void docsIndexAndPerformanceBatchLinkBudgetMatrix() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));
        String performanceBatch = Files.readString(RepositoryPaths.root().resolve("docs/performance-batch.md"));
        String testMatrix = Files.readString(RepositoryPaths.root().resolve("docs/test-pattern-matrix.md"));

        assertTrue(docsIndex.contains("`performance-budget-matrix.md`"));
        assertTrue(performanceBatch.contains("`docs/performance-budget-matrix.md`"));
        assertTrue(performanceBatch.contains("`scripts/perf-memory-budget`"));
        assertTrue(testMatrix.contains("`docs/performance-budget-matrix.md`"));
        assertTrue(testMatrix.contains("large-workspace max-RSS lane"));
    }
}
