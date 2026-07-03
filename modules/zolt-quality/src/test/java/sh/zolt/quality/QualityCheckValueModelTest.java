package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QualityCheckValueModelTest {
    @Test
    void requestNormalizesPathsAndDefensivelyCopiesChecks() {
        List<String> checks = new ArrayList<>(List.of(QualityCheckService.LOCKFILE));

        QualityCheckRequest request = new QualityCheckRequest(
                Path.of("demo/..").resolve("demo"),
                Path.of("cache/..").resolve("cache"),
                true,
                false,
                checks,
                QualityCheckContext.CI,
                Path.of("target/test-reports"),
                Path.of("target/coverage"),
                true,
                true,
                true,
                WorkspaceSelectionRequest.defaults());
        checks.add(QualityCheckService.PROJECT_MODEL);

        assertEquals(Path.of("demo").toAbsolutePath().normalize(), request.projectRoot());
        assertEquals(Path.of("cache").toAbsolutePath().normalize(), request.cacheRoot());
        assertEquals(List.of(QualityCheckService.LOCKFILE), request.checks());
        assertThrows(UnsupportedOperationException.class, () -> request.checks().add(QualityCheckService.PROJECT_MODEL));
    }

    @Test
    void resultFactoriesSetSeverityStatusAndNormalizeNullMember() {
        QualityCheckResult passed = QualityCheckResult.passed("id", null, "subject", "message");
        QualityCheckResult failed = QualityCheckResult.failed("id", Optional.of("apps/api"), "subject", "message", "next");
        QualityCheckResult warning = QualityCheckResult.warning("id", Optional.empty(), "subject", "message", "next");
        QualityCheckResult skipped = QualityCheckResult.skipped("id", Optional.empty(), "subject", "message", "next");

        assertEquals(Optional.empty(), passed.member());
        assertEquals(QualityCheckSeverity.INFO, passed.severity());
        assertEquals(QualityCheckStatus.PASSED, passed.status());
        assertEquals("", passed.nextStep());
        assertEquals(Optional.of("apps/api"), failed.member());
        assertEquals(QualityCheckSeverity.ERROR, failed.severity());
        assertEquals(QualityCheckStatus.FAILED, failed.status());
        assertEquals(QualityCheckSeverity.WARN, warning.severity());
        assertEquals(QualityCheckStatus.WARNING, warning.status());
        assertEquals(QualityCheckSeverity.INFO, skipped.severity());
        assertEquals(QualityCheckStatus.SKIPPED, skipped.status());
    }

    @Test
    void reportTreatsWarningsAndSkipsAsNonBlocking() {
        QualityCheckReport report = new QualityCheckReport(
                Path.of("demo"),
                false,
                List.of(
                        QualityCheckResult.warning("warn", Optional.empty(), "subject", "message", "next"),
                        QualityCheckResult.skipped("skip", Optional.empty(), "subject", "message", "next")));

        assertEquals(true, report.ok());
        assertEquals("ok", report.status());
        assertEquals(0, report.passedCount());
        assertEquals(0, report.failedCount());
        assertEquals(1, report.skippedCount());
    }
}
