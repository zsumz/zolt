package sh.zolt.quality.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.publish.PublishDryRunService;
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

final class PublishDryRunQualityCheckTest {
    private final PublishDryRunQualityCheck check = new PublishDryRunQualityCheck(new PublishDryRunService());

    @TempDir
    private Path tempDir;

    @Test
    void skipsWhenNotCiOrDryRunIsNotRequired() {
        assertEquals(List.of(), check.check(Optional.empty(), tempDir, QualityCheckContext.LOCAL, true));
        assertEquals(List.of(), check.check(Optional.empty(), tempDir, QualityCheckContext.CI, false));
    }

    @Test
    void rejectsWorkspaceMemberDryRunWithMemberScopedNextStep() {
        QualityCheckResult result = check.check(
                Optional.of("modules/api"),
                tempDir,
                QualityCheckContext.CI,
                true).getFirst();

        assertResult(
                result,
                "publish-dry-run",
                "CI publish dry-run preflight is not available for workspace members yet.",
                "Run `zolt publish --dry-run` from the publishable member project, or omit --require-publish-dry-run for workspace checks.");
        assertEquals(Optional.of("modules/api"), result.member());
    }

    @Test
    void mapsPublishPlannerExceptionsToActionableCheckFailure() throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [project]
                name = "publish-dry-run"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        QualityCheckResult result = check.check(Optional.empty(), tempDir, QualityCheckContext.CI, true).getFirst();

        assertResult(
                result,
                "publish-dry-run",
                "CI publish dry-run preflight failed: No [publish] configuration found. Add release/snapshot publish repositories before running `zolt publish --dry-run`.",
                "Configure [publish], run `zolt package`, then retry `zolt check --context ci --require-publish-dry-run`.");
    }

    private static void assertResult(
            QualityCheckResult result,
            String subject,
            String message,
            String nextStep) {
        assertEquals(QualityCheckService.EXECUTION_CONTEXT, result.id());
        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals(subject, result.subject());
        assertEquals(message, result.message());
        assertEquals(nextStep, result.nextStep());
    }
}
