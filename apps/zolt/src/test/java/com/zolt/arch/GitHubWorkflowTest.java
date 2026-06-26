package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GitHubWorkflowTest {
    private static final Path CI_WORKFLOW = RepositoryPaths.root().resolve(".github/workflows/ci.yml");

    @Test
    void ciFansOutShardedTestExecutionThroughZoltPlanAndConsumer() throws IOException {
        String workflow = Files.readString(CI_WORKFLOW);

        assertTrue(workflow.contains("sharded_test_plan:"));
        assertTrue(workflow.contains("scripts/plan-test-shard-matrix"));
        assertTrue(workflow.contains("--output target/test-shard-matrices/test-execution.json"));
        assertTrue(workflow.contains("id: github_matrix"));
        assertTrue(workflow.contains("scripts/test-plan-github-matrix target/test-shard-matrices/test-execution.json"));
        assertTrue(workflow.contains("name: sharded-test-execution-plan"));
        assertTrue(workflow.contains("sharded_test_execution:"));
        assertTrue(workflow.contains("matrix: ${{ fromJson(needs.sharded_test_plan.outputs.matrix) }}"));
        assertTrue(workflow.contains("actions/download-artifact@v4"));
        assertTrue(workflow.contains("scripts/run-test-plan-shard target/test-shard-matrices/test-execution.json \"${{ matrix.selector }}\""));
        assertTrue(workflow.contains("name: sharded-test-execution-evidence-${{ matrix.index }}"));
        assertFalse(workflow.contains("run: scripts/self-host-test-execution-shards"));
    }
}
