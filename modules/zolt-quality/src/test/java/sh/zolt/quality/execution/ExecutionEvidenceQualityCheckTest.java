package sh.zolt.quality.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quality.QualityCheckContext;
import sh.zolt.quality.QualityCheckResult;
import sh.zolt.quality.QualityCheckService;
import sh.zolt.quality.QualityCheckStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExecutionEvidenceQualityCheckTest {
    private final ExecutionEvidenceQualityCheck check = new ExecutionEvidenceQualityCheck();

    @TempDir
    private Path tempDir;

    @Test
    void skipsEvidenceChecksOutsideCiOrWhenDirectoriesAreNotRequested() {
        assertEquals(List.of(), check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.LOCAL));
        assertEquals(List.of(), check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                null,
                null,
                Path.of("target"),
                QualityCheckContext.CI));
    }

    @Test
    void testReportsRequireReportDirectoryWithCommandNextStep() {
        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/test-reports",
                "CI context expected JUnit XML reports, but the report directory is missing.",
                "Run `zolt test --reports-dir target/test-reports` before `zolt check --context ci --reports-dir target/test-reports`.");
    }

    @Test
    void testReportsRequireReportDirectoryWithWorkspaceNextStep() {
        QualityCheckResult result = check.checkTestReports(
                Optional.of("modules/api"),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/test-reports",
                "CI context expected JUnit XML reports, but the report directory is missing.",
                "Run `zolt test --workspace --reports-dir target/test-reports` before `zolt check --workspace --context ci --reports-dir target/test-reports`.");
        assertEquals(Optional.of("modules/api"), result.member());
    }

    @Test
    void testReportsRejectEscapingReportsDirectory() {
        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("../outside-reports"),
                Path.of("../outside-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertEquals(QualityCheckService.EXECUTION_CONTEXT, result.id());
        assertEquals("--reports-dir", result.subject());
        assertTrue(result.message().contains("Invalid --reports-dir path `../outside-reports` resolved to "));
        assertEquals("Use a path such as `target/test-reports`.", result.nextStep());
    }

    @Test
    void testReportsRequireJUnitXmlFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-reports"));
        Files.writeString(tempDir.resolve("target/test-reports/not-junit.xml"), "<testsuite />\n");

        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/test-reports",
                "CI context expected JUnit XML reports, but none were found.",
                "Run `zolt test --reports-dir target/test-reports` before `zolt check --context ci --reports-dir target/test-reports`.");
    }

    @Test
    void testReportsPassWithOneJUnitXmlReport() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-reports"));
        Files.writeString(tempDir.resolve("target/test-reports/TEST-demo.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/OTHER-demo.xml"), "<testsuite />\n");

        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "test-reports",
                "CI test report preflight found 1 JUnit XML report.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void testReportsPluralizeMultipleJUnitXmlReports() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-reports"));
        Files.writeString(tempDir.resolve("target/test-reports/TEST-alpha.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/TEST-beta.xml"), "<testsuite />\n");

        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "test-reports",
                "CI test report preflight found 2 JUnit XML reports.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void testReportsRequireShardAndWorkerEvidenceWhenManifestsExist() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-reports/workers"));
        Files.writeString(tempDir.resolve("target/test-reports/TEST-root.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/workers/zolt-workers.json"), """
                {"workers": ["worker-a"]}
                """);
        Files.createDirectories(tempDir.resolve("target/test-shards/slow-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/slow-suite/shard-1-of-2.json"), """
                {"suite": "slow suite", "empty": false}
                """);

        List<QualityCheckResult> results = check.checkTestReports(
                Optional.of("modules/api"),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI);

        assertEquals(List.of(
                        "target/test-reports/shards/slow-suite/shard-1-of-2|CI context expected JUnit XML reports for shard `slow suite/shard-1-of-2`, but none were found.|Run `zolt test --workspace --suite \"slow suite\" --shard 1/2 --reports-dir target/test-reports`, then rerun `zolt check --context ci --reports-dir target/test-reports`.",
                        "target/test-reports/workers/worker-a|CI context expected JUnit XML reports for worker `worker-a`, but none were found.|Rerun `zolt test --reports-dir target/test-reports` so worker report evidence is regenerated."),
                results.stream()
                        .map(result -> result.subject() + "|" + result.message() + "|" + result.nextStep())
                        .toList());
    }

    @Test
    void testReportsPassWhenShardAndWorkerEvidenceHaveJUnitXmlReports() throws IOException {
        Files.createDirectories(tempDir.resolve("target/test-reports/shards/slow-suite/shard-1-of-2"));
        Files.createDirectories(tempDir.resolve("target/test-reports/workers/worker-a"));
        Files.writeString(tempDir.resolve("target/test-reports/TEST-root.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/shards/slow-suite/shard-1-of-2/TEST-shard.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/workers/worker-a/TEST-worker.xml"), "<testsuite />\n");
        Files.writeString(tempDir.resolve("target/test-reports/workers/zolt-workers.json"), """
                {"workers": ["worker-a"]}
                """);
        Files.createDirectories(tempDir.resolve("target/test-shards/slow-suite"));
        Files.writeString(tempDir.resolve("target/test-shards/slow-suite/shard-1-of-2.json"), """
                {"suite": "slow suite", "empty": false}
                """);

        QualityCheckResult result = check.checkTestReports(
                Optional.empty(),
                tempDir,
                Path.of("target/test-reports"),
                Path.of("target/test-reports"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "test-reports",
                "CI test report preflight found 3 JUnit XML reports.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void coverageRequiresCoverageDirectoryWithWorkspaceNextStep() {
        QualityCheckResult result = check.checkCoverageReports(
                Optional.of("modules/api"),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage",
                "CI context expected coverage reports, but the coverage directory is missing.",
                "Run `zolt coverage` from each selected member so coverage evidence exists under target/coverage, then rerun `zolt check --workspace --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coverageRejectsEscapingCoverageDirectory() {
        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("../outside-coverage"),
                Path.of("../outside-coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertEquals(QualityCheckService.EXECUTION_CONTEXT, result.id());
        assertEquals("--coverage-dir", result.subject());
        assertTrue(result.message().contains("Invalid --coverage-dir path `../outside-coverage` resolved to "));
        assertEquals("Use a path such as `target/coverage`.", result.nextStep());
    }

    @Test
    void coverageRequiresJacocoExecWhenNoSplitEvidenceExists() throws IOException {
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
                "target/coverage/jacoco.exec",
                "CI context expected Jacoco execution data, but jacoco.exec is missing.",
                "Run `zolt coverage` so coverage evidence exists under target/coverage, then rerun `zolt check --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coverageRequiresXmlOrHtmlReportsWhenExecExists() throws IOException {
        Files.createDirectories(tempDir.resolve("target/coverage"));
        Files.writeString(tempDir.resolve("target/coverage/jacoco.exec"), "exec\n");

        QualityCheckResult result = check.checkCoverageReports(
                Optional.empty(),
                tempDir,
                Path.of("target/coverage"),
                Path.of("target/coverage"),
                Path.of("target"),
                QualityCheckContext.CI).getFirst();

        assertResult(
                result,
                "target/coverage",
                "CI context expected coverage XML or HTML reports, but none were found.",
                "Run `zolt coverage` so coverage evidence exists under target/coverage, then rerun `zolt check --context ci --coverage-dir target/coverage`.");
    }

    @Test
    void coveragePassesWithJacocoExecAndReports() throws IOException {
        Files.createDirectories(tempDir.resolve("target/coverage/html"));
        Files.writeString(tempDir.resolve("target/coverage/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/jacoco.xml"), "<report />\n");
        Files.writeString(tempDir.resolve("target/coverage/html/index.html"), "<html></html>\n");

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
                "CI coverage preflight found Jacoco execution data, 1 XML report, and 1 HTML report.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

    @Test
    void coveragePassesWithOnlyXmlReportAndZeroHtmlReports() throws IOException {
        Files.createDirectories(tempDir.resolve("target/coverage"));
        Files.writeString(tempDir.resolve("target/coverage/jacoco.exec"), "exec\n");
        Files.writeString(tempDir.resolve("target/coverage/jacoco.xml"), "<report />\n");

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
                "CI coverage preflight found Jacoco execution data, 1 XML report, and 0 HTML reports.",
                "");
        assertEquals(QualityCheckStatus.PASSED, result.status());
    }

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
