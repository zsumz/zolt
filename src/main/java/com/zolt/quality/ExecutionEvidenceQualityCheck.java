package com.zolt.quality;

import static com.zolt.quality.QualityCheckService.EXECUTION_CONTEXT;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ExecutionEvidenceQualityCheck {
    List<QualityCheckResult> checkTestReports(
            Optional<String> member,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir,
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
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "test-reports",
                    "CI test report preflight found "
                            + xmlReports
                            + " JUnit XML "
                            + (xmlReports == 1 ? "report." : "reports.")));
        } catch (java.io.IOException exception) {
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

    private static long countJUnitXmlReports(Path projectRoot, Path reportsDir) throws java.io.IOException {
        try (Stream<Path> paths = Files.walk(reportsDir)) {
            return paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(projectRoot, "--reports-dir", path))
                    .filter(ExecutionEvidenceQualityCheck::isJUnitXmlReport)
                    .count();
        }
    }

    private static boolean isJUnitXmlReport(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
    }

    List<QualityCheckResult> checkCoverageReports(
            Optional<String> member,
            Path projectRoot,
            Path coverageDir,
            Path commandCoverageDir,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI || coverageDir == null) {
            return List.of();
        }
        Path root = ProjectPaths.root(projectRoot);
        Path absoluteCoverageDir;
        try {
            absoluteCoverageDir = ProjectPaths.output(root, "--coverage-dir", coverageDir.toString());
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "--coverage-dir",
                    exception.getMessage(),
                    "Use a path such as `target/coverage`."));
        }
        if (!Files.isDirectory(absoluteCoverageDir)) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteCoverageDir),
                    "CI context expected coverage reports, but the coverage directory is missing.",
                    coverageReportsNextStep(member, commandCoverageDir)));
        }
        try {
            Path execFile = absoluteCoverageDir.resolve("jacoco.exec");
            if (!ProjectPaths.isRegularFileInsideProject(root, "--coverage-dir", execFile)) {
                return List.of(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, execFile),
                        "CI context expected Jacoco execution data, but jacoco.exec is missing.",
                        coverageReportsNextStep(member, commandCoverageDir)));
            }
            CoverageReportCounts counts = countCoverageReports(root, absoluteCoverageDir);
            if (counts.xmlReports() == 0 && counts.htmlReports() == 0) {
                return List.of(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, absoluteCoverageDir),
                        "CI context expected coverage XML or HTML reports, but none were found.",
                        coverageReportsNextStep(member, commandCoverageDir)));
            }
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "coverage-reports",
                    "CI coverage preflight found Jacoco execution data, "
                            + counts.xmlReports()
                            + " XML "
                            + QualityCheckText.plural(counts.xmlReports(), "report", "reports")
                            + ", and "
                            + counts.htmlReports()
                            + " HTML "
                            + QualityCheckText.plural(counts.htmlReports(), "report", "reports")
                            + "."));
        } catch (java.io.IOException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteCoverageDir),
                    "Could not inspect coverage reports: " + exception.getMessage(),
                    "Check coverage directory permissions, then rerun `zolt check --context ci --coverage-dir " + commandCoverageDir + "`."));
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    QualityCheckText.displayPath(projectRoot, absoluteCoverageDir),
                    exception.getMessage(),
                    "Remove symlinked coverage entries that escape the project, then rerun `zolt check --context ci --coverage-dir " + commandCoverageDir + "`."));
        }
    }

    private static String coverageReportsNextStep(Optional<String> member, Path commandCoverageDir) {
        if (member.isPresent()) {
            return "Run `zolt coverage` from each selected member so coverage evidence exists under "
                    + commandCoverageDir
                    + ", then rerun `zolt check --workspace --context ci --coverage-dir "
                    + commandCoverageDir
                    + "`.";
        }
        return "Run `zolt coverage` so coverage evidence exists under "
                + commandCoverageDir
                + ", then rerun `zolt check --context ci --coverage-dir "
                + commandCoverageDir
                + "`.";
    }

    private static CoverageReportCounts countCoverageReports(Path projectRoot, Path coverageDir) throws java.io.IOException {
        long xmlReports;
        long htmlReports;
        try (Stream<Path> paths = Files.walk(coverageDir)) {
            List<Path> files = paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(projectRoot, "--coverage-dir", path))
                    .toList();
            xmlReports = files.stream().filter(ExecutionEvidenceQualityCheck::isCoverageXmlReport).count();
            htmlReports = files.stream().filter(ExecutionEvidenceQualityCheck::isCoverageHtmlReport).count();
        }
        return new CoverageReportCounts(xmlReports, htmlReports);
    }

    private static boolean isCoverageXmlReport(Path path) {
        return path.getFileName().toString().endsWith(".xml");
    }

    private static boolean isCoverageHtmlReport(Path path) {
        return path.getFileName().toString().endsWith(".html");
    }

    private record CoverageReportCounts(long xmlReports, long htmlReports) {
    }
}
