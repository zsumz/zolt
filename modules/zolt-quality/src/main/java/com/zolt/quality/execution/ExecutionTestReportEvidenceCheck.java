package com.zolt.quality.execution;

import static com.zolt.quality.QualityCheckService.EXECUTION_CONTEXT;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.quality.QualityCheckContext;
import com.zolt.quality.QualityCheckResult;
import com.zolt.quality.QualityCheckText;
import com.zolt.quality.execution.ExecutionSplitEvidence.ShardEvidenceManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ExecutionTestReportEvidenceCheck {
    private final ExecutionSplitEvidence splitEvidence;

    ExecutionTestReportEvidenceCheck(ExecutionSplitEvidence splitEvidence) {
        this.splitEvidence = splitEvidence;
    }

    List<QualityCheckResult> check(
            Optional<String> member,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir,
            Path outputRoot,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI || reportsDir == null) {
            return List.of();
        }
        Path root = ProjectPaths.root(projectRoot);
        Path absoluteReportsDir;
        try {
            absoluteReportsDir = ProjectPaths.output(root, "--reports-dir", reportsDir.toString());
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "--reports-dir",
                    exception.getMessage(),
                    "Use a path such as `target/test-reports`."));
        }
        if (!Files.isDirectory(absoluteReportsDir)) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteReportsDir),
                    "CI context expected JUnit XML reports, but the report directory is missing.",
                    testReportsNextStep(member, commandReportsDir)));
        }
        try {
            long xmlReports = countJUnitXmlReports(root, absoluteReportsDir);
            if (xmlReports == 0) {
                return List.of(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, absoluteReportsDir),
                        "CI context expected JUnit XML reports, but none were found.",
                        testReportsNextStep(member, commandReportsDir)));
            }
            List<QualityCheckResult> splitEvidenceFailures = new ArrayList<>();
            splitEvidenceFailures.addAll(checkShardTestReports(
                    member,
                    root,
                    projectRoot,
                    absoluteReportsDir,
                    commandReportsDir,
                    outputRoot));
            splitEvidenceFailures.addAll(checkWorkerTestReports(
                    member,
                    root,
                    projectRoot,
                    absoluteReportsDir,
                    commandReportsDir));
            if (!splitEvidenceFailures.isEmpty()) {
                return splitEvidenceFailures;
            }
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "test-reports",
                    "CI test report preflight found "
                            + xmlReports
                            + " JUnit XML "
                            + (xmlReports == 1 ? "report." : "reports.")));
        } catch (IOException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteReportsDir),
                    "Could not inspect JUnit XML reports: " + exception.getMessage(),
                    "Check report directory permissions, then rerun `zolt check --context ci --reports-dir " + commandReportsDir + "`."));
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteReportsDir),
                    exception.getMessage(),
                    "Remove symlinked report entries that escape the project, then rerun `zolt check --context ci --reports-dir " + commandReportsDir + "`."));
        }
    }

    private static String testReportsNextStep(Optional<String> member, Path commandReportsDir) {
        if (member.isPresent()) {
            return "Run `zolt test --workspace --reports-dir "
                    + commandReportsDir
                    + "` before `zolt check --workspace --context ci --reports-dir "
                    + commandReportsDir
                    + "`.";
        }
        return "Run `zolt test --reports-dir "
                + commandReportsDir
                + "` before `zolt check --context ci --reports-dir "
                + commandReportsDir
                + "`.";
    }

    private static long countJUnitXmlReports(Path projectRoot, Path reportsDir) throws IOException {
        try (Stream<Path> paths = Files.walk(reportsDir)) {
            return paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(projectRoot, "--reports-dir", path))
                    .filter(ExecutionTestReportEvidenceCheck::isJUnitXmlReport)
                    .count();
        }
    }

    private static boolean isJUnitXmlReport(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
    }

    private List<QualityCheckResult> checkShardTestReports(
            Optional<String> member,
            Path root,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir,
            Path outputRoot) throws IOException {
        List<QualityCheckResult> failures = new ArrayList<>();
        for (ShardEvidenceManifest manifest : splitEvidence.nonEmpty(splitEvidence.shardManifests(root, outputRoot))) {
            Path expectedDir = reportsDir.resolve("shards").resolve(manifest.suiteSegment()).resolve(manifest.shardSegment());
            if (!Files.isDirectory(expectedDir) || countJUnitXmlReports(root, expectedDir) == 0) {
                failures.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, expectedDir),
                        "CI context expected JUnit XML reports for shard `"
                                + manifest.displayName()
                                + "`, but none were found.",
                        testShardNextStep(member, manifest, commandReportsDir)));
            }
        }
        return failures;
    }

    private List<QualityCheckResult> checkWorkerTestReports(
            Optional<String> member,
            Path root,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir) throws IOException {
        List<QualityCheckResult> failures = new ArrayList<>();
        for (String workerId : splitEvidence.workerIds(reportsDir.resolve("workers").resolve("zolt-workers.json"))) {
            Path expectedDir = reportsDir.resolve("workers").resolve(workerId);
            if (!Files.isDirectory(expectedDir) || countJUnitXmlReports(root, expectedDir) == 0) {
                failures.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, expectedDir),
                        "CI context expected JUnit XML reports for worker `"
                                + workerId
                                + "`, but none were found.",
                        "Rerun `zolt test --reports-dir "
                                + commandReportsDir
                                + "` so worker report evidence is regenerated."));
            }
        }
        return failures;
    }

    private String testShardNextStep(Optional<String> member, ShardEvidenceManifest manifest, Path commandReportsDir) {
        String workspace = member.isPresent() ? " --workspace" : "";
        return "Run `zolt test"
                + workspace
                + " --suite "
                + splitEvidence.shellArgument(manifest.suiteName())
                + " --shard "
                + manifest.index()
                + "/"
                + manifest.total()
                + " --reports-dir "
                + commandReportsDir
                + "`, then rerun `zolt check --context ci --reports-dir "
                + commandReportsDir
                + "`.";
    }
}
