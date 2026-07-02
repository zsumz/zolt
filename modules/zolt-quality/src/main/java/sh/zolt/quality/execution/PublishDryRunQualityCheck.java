package sh.zolt.quality.execution;

import static sh.zolt.quality.QualityCheckService.EXECUTION_CONTEXT;

import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishDryRunService;
import sh.zolt.publish.PublishException;
import sh.zolt.quality.QualityCheckContext;
import sh.zolt.quality.QualityCheckResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PublishDryRunQualityCheck {
    private final PublishDryRunService publishDryRunService;

    PublishDryRunQualityCheck(PublishDryRunService publishDryRunService) {
        this.publishDryRunService = publishDryRunService;
    }

    List<QualityCheckResult> check(
            Optional<String> member,
            Path projectRoot,
            QualityCheckContext context,
            boolean requirePublishDryRun) {
        if (context != QualityCheckContext.CI || !requirePublishDryRun) {
            return List.of();
        }
        if (member.isPresent()) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight is not available for workspace members yet.",
                    "Run `zolt publish --dry-run` from the publishable member project, or omit --require-publish-dry-run for workspace checks."));
        }
        try {
            PublishDryRunPlan plan = publishDryRunService.plan(projectRoot);
            if (!plan.ok()) {
                List<QualityCheckResult> results = new ArrayList<>();
                for (String blocker : plan.blockers()) {
                    results.add(QualityCheckResult.failed(
                            EXECUTION_CONTEXT,
                            member,
                            "publish-dry-run",
                            "CI publish dry-run blocker: " + blocker,
                            "Run `zolt publish --dry-run` and resolve the reported blocker before release CI."));
                }
                return List.copyOf(results);
            }
            int artifactCount = 1 + plan.supplementalArtifacts().size();
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight is ready for "
                            + plan.coordinate()
                            + " to repository `"
                            + plan.repositoryId()
                            + "` with "
                            + artifactCount
                            + " "
                            + (artifactCount == 1 ? "artifact" : "artifacts")
                            + " and generated POM metadata."));
        } catch (PublishException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight failed: " + exception.getMessage(),
                    "Configure [publish], run `zolt package`, then retry `zolt check --context ci --require-publish-dry-run`."));
        }
    }
}
