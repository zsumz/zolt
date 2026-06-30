package com.zolt.quality;

import static com.zolt.quality.QualityCheckService.DEPENDENCY_POLICY;

import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.toml.ZoltLockfileReader;
import com.zolt.policy.DependencyPolicyReport;
import com.zolt.policy.DependencyPolicyReportException;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.project.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class DependencyPolicyQualityCheck {
    private final ZoltLockfileReader lockfileReader;
    private final DependencyPolicyReportService dependencyPolicyReportService;

    DependencyPolicyQualityCheck(
            ZoltLockfileReader lockfileReader,
            DependencyPolicyReportService dependencyPolicyReportService) {
        this.lockfileReader = lockfileReader;
        this.dependencyPolicyReportService = dependencyPolicyReportService;
    }

    List<QualityCheckResult> check(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            Path lockfilePath,
            boolean workspaceLockfile) {
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_POLICY,
                    member,
                    "zolt.lock",
                    "Dependency policy diagnostics require zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_POLICY,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    workspaceLockfile
                            ? "Run `zolt resolve --workspace` to refresh dependency policy evidence."
                            : "Run `zolt resolve` to refresh dependency policy evidence."));
        }

        try {
            DependencyPolicyReport report = dependencyPolicyReportService.report(root, config, lockfile);
            List<QualityCheckResult> results = new ArrayList<>();
            results.add(summary(member, config, report));
            addConstraintDiagnostics(results, member, report);
            addExclusionDiagnostics(results, member, report);
            addDirectVersionDiagnostics(results, member, report);
            return List.copyOf(results);
        } catch (DependencyPolicyReportException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_POLICY,
                    member,
                    "zolt.toml",
                    exception.getMessage(),
                    "Fix dependency policy coordinates, then run `zolt check --check dependency-policy` again."));
        }
    }

    private static QualityCheckResult summary(
            Optional<String> member,
            ProjectConfig config,
            DependencyPolicyReport report) {
        return QualityCheckResult.passed(
                DEPENDENCY_POLICY,
                member,
                config.project().name(),
                "Dependency policy baseline is explainable: "
                        + report.platforms().size()
                        + " "
                        + QualityCheckText.plural(report.platforms().size(), "platform", "platforms")
                        + ", "
                        + report.constraints().size()
                        + " "
                        + QualityCheckText.plural(report.constraints().size(), "constraint", "constraints")
                        + ", "
                        + report.exclusions().size()
                        + " "
                        + QualityCheckText.plural(report.exclusions().size(), "exclusion", "exclusions")
                        + ", and "
                        + report.directVersions().size()
                        + " direct explicit "
                        + QualityCheckText.plural(report.directVersions().size(), "version", "versions")
                        + ".");
    }

    private static void addConstraintDiagnostics(
            List<QualityCheckResult> results,
            Optional<String> member,
            DependencyPolicyReport report) {
        for (DependencyPolicyReport.ConstraintPolicyDiagnostic constraint : report.constraints()) {
            if ("conflict".equals(constraint.status())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_POLICY,
                        member,
                        "[dependencyConstraints]." + constraint.coordinate(),
                        "Strict constraint expected `"
                                + constraint.coordinate()
                                + "` version `"
                                + constraint.requestedVersion()
                                + "`, but zolt.lock selected `"
                                + constraint.selectedVersion().orElse("none")
                                + "`.",
                        "Run `zolt resolve` after updating [dependencyConstraints], or change the strict constraint to the selected baseline."));
            } else if ("direct-override".equals(constraint.status())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_POLICY,
                        member,
                        "[dependencyConstraints]." + constraint.coordinate(),
                        "Strict constraint for `"
                                + constraint.coordinate()
                                + "` is overridden by a direct dependency version.",
                        "Align the direct dependency version with [dependencyConstraints], or remove the strict constraint if the direct override is intentional."));
            }
        }
    }

    private static void addExclusionDiagnostics(
            List<QualityCheckResult> results,
            Optional<String> member,
            DependencyPolicyReport report) {
        for (DependencyPolicyReport.ExclusionPolicyDiagnostic exclusion : report.exclusions()) {
            if ("direct-conflict".equals(exclusion.status())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_POLICY,
                        member,
                        "[dependencyPolicy].exclude " + exclusion.coordinate(),
                        "Dependency policy excludes `"
                                + exclusion.coordinate()
                                + "`, but that package is still a direct dependency.",
                        "Remove the direct dependency, or remove the exclusion if the dependency is intentional."));
            }
        }
    }

    private static void addDirectVersionDiagnostics(
            List<QualityCheckResult> results,
            Optional<String> member,
            DependencyPolicyReport report) {
        for (DependencyPolicyReport.DirectVersionDiagnostic direct : report.directVersions()) {
            if ("not-selected".equals(direct.status())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_POLICY,
                        member,
                        "[" + direct.section() + "]." + direct.coordinate(),
                        "Direct dependency `"
                                + direct.coordinate()
                                + ":"
                                + direct.version()
                                + "` is declared, but zolt.lock did not select that version.",
                        "Run `zolt resolve`, then review the selected version or update the direct dependency declaration."));
            }
        }
    }
}
