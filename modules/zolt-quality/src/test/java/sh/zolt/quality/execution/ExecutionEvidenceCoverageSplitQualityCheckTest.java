package sh.zolt.quality.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.quality.QualityCheckContext;
import sh.zolt.quality.QualityCheckResult;
import sh.zolt.quality.QualityCheckService;
import sh.zolt.quality.QualityCheckStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExecutionEvidenceCoverageSplitQualityCheckTest {
    private final ExecutionEvidenceQualityCheck check = new ExecutionEvidenceQualityCheck();

    @TempDir
    private Path tempDir;

    @Test
    void coveragePassesWithSplitShardAndWorkerEvidenceWhenRootExecIsMissing() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-shards/unit-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/unit-suite/shard-1-of-1.json"), """
                {"suite": "unit", "empty": false}
                """);
        Files.createDirectories(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1"));
        Files.writeString(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1/jacoco.xml"), "<report />\n");
        Files.createDirectories(tempDir.resolve("target/coverage/workers/worker-a"));
        Files.writeString(tempDir.resolve("target/coverage/workers/zolt-workers.json"), """
                {"workers": ["worker-a"]}
                """);
        Files.writeString(tempDir.resolve("target/coverage/workers/worker-a/jacoco.exec"), "exec\n");

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "coverage-reports",
                "CI coverage preflight found split Jacoco evidence.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void coveragePassesWithRootExecAndCompleteSplitEvidence() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-shards/unit-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/unit-suite/shard-1-of-1.json"), """
                {"suite": "unit", "empty": false}
                """);
        Files.createDirectories(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1"));
        Files.createDirectories(tempDir.resolve("target/coverage/workers/worker-a"));
        Files.writeString(tempDir.resolve("target/coverage/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/jacoco.xml"), "<report />\n");
        Files.writeString(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1/jacoco.xml"), "<report />\n");
        Files.writeString(tempDir.resolve("target/coverage/workers/zolt-workers.json"), """
                {"workers": ["worker-a"]}
                """);
        Files.writeString(tempDir.resolve("target/coverage/workers/worker-a/jacoco.exec"), "exec\n");

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "coverage-reports",
                "CI coverage preflight found Jacoco execution data, 2 XML reports, and 0 HTML reports.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void coverageReportsMissingShardEvidenceWithRerunCommand() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-shards/unit-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/unit-suite/shard-1-of-2.json"), """
                {"suite": "unit suite", "empty": false}
                """);
        Files.createDirectories(tempDir.resolve("target/coverage"));

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage/shards/unit-suite/shard-1-of-2/jacoco.exec",
                "CI context expected Jacoco execution data for shard `unit suite/shard-1-of-2`, but jacoco.exec is missing.",
                "Run `zolt coverage --suite \"unit suite\" --shard 1/2`, then rerun `zolt check --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coverageReportsMissingWorkspaceShardEvidenceWithWorkspaceRerunCommand() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-shards/unit-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/unit-suite/shard-2-of-3.json"), """
                {"suite": "unit suite", "empty": false}
                """);
        Files.createDirectories(tempDir.resolve("target/coverage"));

        QualityCheckResult result = check.checkCoverageReports(
                Optional.of("modules/api"),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage/shards/unit-suite/shard-2-of-3/jacoco.exec",
                "CI context expected Jacoco execution data for shard `unit suite/shard-2-of-3`, but jacoco.exec is missing.",
                "Run `zolt coverage --workspace --suite \"unit suite\" --shard 2/3`, then rerun `zolt check --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coverageReportsShardExecWithoutXmlOrHtmlEvidence() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-shards/unit-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/unit-suite/shard-1-of-1.json"), """
                {"suite": "unit", "empty": false}
                """);
        Files.createDirectories(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1"));
        Files.writeString(tempDir.resolve("target/coverage/shards/unit-suite/shard-1-of-1/jacoco.exec"), "exec\n");

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage/shards/unit-suite/shard-1-of-1",
                "CI context expected coverage XML or HTML reports for shard `unit/shard-1-of-1`, but none were found.",
                "Run `zolt coverage --suite unit --shard 1/1`, then rerun `zolt check --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coverageReportsMissingWorkerExecEvidence() throws IOException {
        Files.createDirectories(tempDir.resolve("target/coverage/workers"));
        Files.writeString(tempDir.resolve("target/coverage/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/jacoco.xml"), "<report />\n");
        Files.writeString(tempDir.resolve("target/coverage/workers/zolt-workers.json"), """
                {"workers": ["worker-a"]}
                """);

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage/workers/worker-a/jacoco.exec",
                "CI context expected Jacoco execution data for worker `worker-a`, but jacoco.exec is missing.",
                "Rerun `zolt coverage` so worker coverage evidence is regenerated under target/coverage.");
    }

    private static void assertResult(
            QualityCheckResult result,
            String subject,
            String message,
            String nextStep) {
        assertEquals(QualityCheckService.EXECUTION_CONTEXT, result.id());
        assertEquals(subject, result.subject());
        assertEquals(message, result.message());
        assertEquals(nextStep, result.nextStep());
        if (!nextStep.isBlank()) {
            assertEquals(QualityCheckStatus.FAILED, result.status());
        }
    }
}
