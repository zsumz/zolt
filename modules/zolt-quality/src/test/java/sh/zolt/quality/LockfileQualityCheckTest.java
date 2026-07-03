package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.ResolveService;
import sh.zolt.workspace.WorkspaceConfig;
import sh.zolt.workspace.resolve.WorkspaceResolveService;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LockfileQualityCheckTest extends QualityCheckServiceTestSupport {
    private final LockfileQualityCheck check = new LockfileQualityCheck(
            new ResolveService(),
            new WorkspaceResolveService(),
            new ZoltLockfileReader());

    @TempDir
    private Path tempDir;

    @Test
    void projectLockfileCheckReportsMissingLockfileBeforeResolve() throws IOException {
        Path projectDir = tempDir.resolve("missing-project-lock");
        ProjectConfig config = parseProject(projectDir, "");

        QualityCheckResult result = check.checkProjectLockfile(request(projectDir), config);

        assertResult(
                result,
                QualityCheckService.LOCKFILE,
                QualityCheckStatus.FAILED,
                "zolt.lock",
                "zolt.lock is missing.",
                "Run `zolt resolve`.");
    }

    @Test
    void projectCacheIntegrityRequiresLockfile() throws IOException {
        Path projectDir = tempDir.resolve("missing-cache-lock");
        Files.createDirectories(projectDir);

        QualityCheckResult result = check.checkProjectCacheIntegrity(request(projectDir));

        assertResult(
                result,
                QualityCheckService.CACHE_INTEGRITY,
                QualityCheckStatus.FAILED,
                "zolt.lock",
                "zolt.lock is missing.",
                "Run `zolt resolve`.");
    }

    @Test
    void workspaceCacheIntegrityUsesWorkspaceResolveNextStep() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace-missing-lock");
        Files.createDirectories(workspaceDir);

        QualityCheckResult result = check.checkWorkspaceCacheIntegrity(request(workspaceDir), workspace(workspaceDir));

        assertResult(
                result,
                QualityCheckService.CACHE_INTEGRITY,
                QualityCheckStatus.FAILED,
                "zolt.lock",
                "zolt.lock is missing.",
                "Run `zolt resolve --workspace`.");
    }

    @Test
    void cacheIntegrityReportsMalformedLockfileWithRefreshNextStep() throws IOException {
        Path projectDir = tempDir.resolve("malformed-lock");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 99
                """);

        QualityCheckResult result = check.checkProjectCacheIntegrity(request(projectDir));

        assertEquals(QualityCheckService.CACHE_INTEGRITY, result.id());
        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("zolt.lock", result.subject());
        assertTrue(result.message().contains("Unsupported zolt.lock version 99."));
        assertEquals("Run `zolt resolve` to regenerate zolt.lock.", result.nextStep());
    }

    @Test
    void cacheIntegrityPassesForEmptyReadableLockfile() throws IOException {
        Path projectDir = tempDir.resolve("empty-lock");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        QualityCheckResult result = check.checkProjectCacheIntegrity(request(projectDir));

        assertResult(
                result,
                QualityCheckService.CACHE_INTEGRITY,
                QualityCheckStatus.PASSED,
                "zolt.lock",
                "All cached artifacts with lockfile checksums match local bytes.",
                "");
    }

    private QualityCheckRequest request(Path projectDir) {
        return new QualityCheckRequest(
                projectDir,
                tempDir.resolve("cache"),
                false,
                false,
                List.of(),
                QualityCheckContext.CI,
                null,
                null,
                false,
                false,
                false,
                WorkspaceSelectionRequest.defaults());
    }

    private static Workspace workspace(Path root) {
        return new Workspace(
                root,
                root.resolve("zolt.toml"),
                new WorkspaceConfig("workspace", List.of(), List.of(), java.util.Map.of(), java.util.Map.of()),
                List.of());
    }

    private static void assertResult(
            QualityCheckResult result,
            String id,
            QualityCheckStatus status,
            String subject,
            String message,
            String nextStep) {
        assertEquals(id, result.id());
        assertEquals(status, result.status());
        assertEquals(subject, result.subject());
        assertEquals(message, result.message());
        assertEquals(nextStep, result.nextStep());
        assertEquals(Optional.empty(), result.member());
    }
}
