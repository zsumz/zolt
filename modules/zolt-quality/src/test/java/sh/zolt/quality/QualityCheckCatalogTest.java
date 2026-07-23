package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QualityCheckCatalogTest {
    @Test
    void selectsDefaultChecksByContext() {
        assertEquals(
                List.of(
                        QualityCheckService.EXECUTION_CONTEXT,
                        QualityCheckService.LOCKFILE,
                        QualityCheckService.PROJECT_MODEL,
                        QualityCheckService.DEPENDENCY_METADATA,
                        QualityCheckService.DEPENDENCY_POLICY,
                        QualityCheckService.LICENSE_POLICY,
                        QualityCheckService.GENERATED_SOURCES,
                        QualityCheckService.PACKAGE_CONTENTS),
                QualityCheckCatalog.requestedChecks(request(List.of(), QualityCheckContext.CI)));
        assertEquals(
                List.of(QualityCheckService.EXECUTION_CONTEXT),
                QualityCheckCatalog.requestedChecks(request(List.of(), QualityCheckContext.LOCAL)));
        assertEquals(
                List.of(QualityCheckService.COMMAND_SURFACE),
                QualityCheckCatalog.requestedChecks(request(List.of(), null)));
    }

    @Test
    void normalizesRequestedChecksAndAddsExecutionContextForCiAndLocal() {
        assertEquals(
                List.of(QualityCheckService.EXECUTION_CONTEXT, QualityCheckService.LOCKFILE, "mvn verify"),
                QualityCheckCatalog.requestedChecks(request(
                        List.of(" lockfile ", "", "mvn verify", "lockfile"),
                        QualityCheckContext.CI)));
        assertEquals(
                List.of(QualityCheckService.LOCKFILE, "mvn verify"),
                QualityCheckCatalog.requestedChecks(request(
                        List.of(" lockfile ", "", "mvn verify", "lockfile"),
                        null)));
    }

    @Test
    void ignoresWhitespaceOnlyRequestedChecks() {
        assertEquals(
                List.of(QualityCheckService.LOCKFILE),
                QualityCheckCatalog.requestedChecks(request(List.of("   ", "\t", QualityCheckService.LOCKFILE), null)));
    }

    @Test
    void formatsUnsupportedCheckWithSupportedChecksAndHookBoundary() {
        QualityCheckResult result = QualityCheckCatalog.unsupportedOrSkipped("mvn verify");

        assertEquals("unsupported-check", result.id());
        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("mvn verify", result.subject());
        assertEquals("Unsupported quality check `mvn verify`.", result.message());
        assertTrue(result.nextStep().contains("Use one of:"));
        assertTrue(result.nextStep().contains("Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks."));
    }

    @Test
    void unavailableResultsFailImplementedChecksAndRejectUnsupportedChecks() {
        List<QualityCheckResult> results = QualityCheckCatalog.unavailableResults(
                List.of(QualityCheckService.COMMAND_SURFACE, "mvn verify"),
                "zolt.toml",
                "Invalid config",
                "Fix zolt.toml.");

        assertEquals(2, results.size());
        assertEquals(QualityCheckService.COMMAND_SURFACE, results.get(0).id());
        assertEquals(QualityCheckStatus.FAILED, results.get(0).status());
        assertEquals("zolt.toml", results.get(0).subject());
        assertEquals("Invalid config", results.get(0).message());
        assertEquals("Fix zolt.toml.", results.get(0).nextStep());
        assertEquals("unsupported-check", results.get(1).id());
        assertEquals("mvn verify", results.get(1).subject());
    }

    private static QualityCheckRequest request(List<String> checks, QualityCheckContext context) {
        return new QualityCheckRequest(
                Path.of("/workspace/demo"),
                Path.of("/workspace/cache"),
                false,
                false,
                checks,
                context,
                null,
                null,
                false,
                false,
                false,
                WorkspaceSelectionRequest.defaults());
    }
}
