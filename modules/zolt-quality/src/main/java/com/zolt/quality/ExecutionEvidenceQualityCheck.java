package com.zolt.quality;

import static com.zolt.quality.QualityCheckService.EXECUTION_CONTEXT;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ExecutionEvidenceQualityCheck {
    List<QualityCheckResult> checkTestReports(
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
            Path outputRoot,
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
            List<ShardEvidenceManifest> shardManifests = shardManifests(root, outputRoot);
            List<String> workerIds = workerIds(absoluteCoverageDir.resolve("workers").resolve("zolt-workers.json"));
            Path execFile = absoluteCoverageDir.resolve("jacoco.exec");
            if (!ProjectPaths.isRegularFileInsideProject(root, "--coverage-dir", execFile)) {
                List<QualityCheckResult> splitCoverageFailures = splitCoverageFailures(
                        member,
                        root,
                        projectRoot,
                        absoluteCoverageDir,
                        commandCoverageDir,
                        shardManifests,
                        workerIds);
                if (splitCoverageFailures.isEmpty() && (!nonEmpty(shardManifests).isEmpty() || !workerIds.isEmpty())) {
                    return List.of(QualityCheckResult.passed(
                            EXECUTION_CONTEXT,
                            member,
                            "coverage-reports",
                            "CI coverage preflight found split Jacoco evidence."));
                }
                if (!splitCoverageFailures.isEmpty()) {
                    return splitCoverageFailures;
                }
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
            List<QualityCheckResult> splitCoverageFailures = splitCoverageFailures(
                    member,
                    root,
                    projectRoot,
                    absoluteCoverageDir,
                    commandCoverageDir,
                    shardManifests,
                    workerIds);
            if (!splitCoverageFailures.isEmpty()) {
                return splitCoverageFailures;
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

    private static List<QualityCheckResult> checkShardTestReports(
            Optional<String> member,
            Path root,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir,
            Path outputRoot) throws java.io.IOException {
        List<QualityCheckResult> failures = new ArrayList<>();
        for (ShardEvidenceManifest manifest : nonEmpty(shardManifests(root, outputRoot))) {
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

    private static List<QualityCheckResult> checkWorkerTestReports(
            Optional<String> member,
            Path root,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir) throws java.io.IOException {
        List<QualityCheckResult> failures = new ArrayList<>();
        for (String workerId : workerIds(reportsDir.resolve("workers").resolve("zolt-workers.json"))) {
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

    private static List<QualityCheckResult> splitCoverageFailures(
            Optional<String> member,
            Path root,
            Path projectRoot,
            Path coverageDir,
            Path commandCoverageDir,
            List<ShardEvidenceManifest> shardManifests,
            List<String> workerIds) throws java.io.IOException {
        List<QualityCheckResult> failures = new ArrayList<>();
        for (ShardEvidenceManifest manifest : nonEmpty(shardManifests)) {
            Path expectedDir = coverageDir.resolve("shards").resolve(manifest.suiteSegment()).resolve(manifest.shardSegment());
            Path execFile = expectedDir.resolve("jacoco.exec");
            if (!ProjectPaths.isRegularFileInsideProject(root, "--coverage-dir", execFile)) {
                failures.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, execFile),
                        "CI context expected Jacoco execution data for shard `"
                                + manifest.displayName()
                                + "`, but jacoco.exec is missing.",
                        coverageShardNextStep(member, manifest, commandCoverageDir)));
                continue;
            }
            CoverageReportCounts counts = countCoverageReports(root, expectedDir);
            if (counts.xmlReports() == 0 && counts.htmlReports() == 0) {
                failures.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, expectedDir),
                        "CI context expected coverage XML or HTML reports for shard `"
                                + manifest.displayName()
                                + "`, but none were found.",
                        coverageShardNextStep(member, manifest, commandCoverageDir)));
            }
        }
        for (String workerId : workerIds) {
            Path execFile = coverageDir.resolve("workers").resolve(workerId).resolve("jacoco.exec");
            if (!ProjectPaths.isRegularFileInsideProject(root, "--coverage-dir", execFile)) {
                failures.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        QualityCheckText.displayPath(projectRoot, execFile),
                        "CI context expected Jacoco execution data for worker `"
                                + workerId
                                + "`, but jacoco.exec is missing.",
                        "Rerun `zolt coverage` so worker coverage evidence is regenerated under "
                                + commandCoverageDir
                                + "."));
            }
        }
        return failures;
    }

    private static List<ShardEvidenceManifest> shardManifests(Path root, Path outputRoot) throws java.io.IOException {
        Path testShardsDir = ProjectPaths.output(root, "test shard evidence", outputRoot.toString()).resolve("test-shards");
        if (!Files.isDirectory(testShardsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(testShardsDir)) {
            List<Path> manifests = paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(root, "test shard evidence", path))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();
            List<ShardEvidenceManifest> evidence = new ArrayList<>();
            for (Path manifest : manifests) {
                shardManifest(testShardsDir, manifest).ifPresent(evidence::add);
            }
            return List.copyOf(evidence);
        }
    }

    private static Optional<ShardEvidenceManifest> shardManifest(Path testShardsDir, Path manifestPath) throws java.io.IOException {
        Path relative = testShardsDir.relativize(manifestPath);
        if (relative.getNameCount() != 2) {
            return Optional.empty();
        }
        String suiteSegment = relative.getName(0).toString();
        String fileName = relative.getName(1).toString();
        if (!fileName.startsWith("shard-") || !fileName.endsWith(".json")) {
            return Optional.empty();
        }
        String shardSegment = fileName.substring(0, fileName.length() - ".json".length());
        Optional<ShardNumbers> numbers = shardNumbers(shardSegment);
        if (numbers.isEmpty()) {
            return Optional.empty();
        }
        String json = Files.readString(manifestPath);
        return Optional.of(new ShardEvidenceManifest(
                stringField(json, "suite").orElse(suiteSegment),
                suiteSegment,
                shardSegment,
                numbers.orElseThrow().index(),
                numbers.orElseThrow().total(),
                json.contains("\"empty\": true")));
    }

    private static Optional<ShardNumbers> shardNumbers(String shardSegment) {
        if (!shardSegment.startsWith("shard-")) {
            return Optional.empty();
        }
        int separator = shardSegment.indexOf("-of-", "shard-".length());
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            int index = Integer.parseInt(shardSegment.substring("shard-".length(), separator));
            int total = Integer.parseInt(shardSegment.substring(separator + "-of-".length()));
            return Optional.of(new ShardNumbers(index, total));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static List<ShardEvidenceManifest> nonEmpty(List<ShardEvidenceManifest> manifests) {
        return manifests.stream()
                .filter(manifest -> !manifest.empty())
                .toList();
    }

    private static List<String> workerIds(Path manifest) throws java.io.IOException {
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        String json = Files.readString(manifest);
        int workersIndex = json.indexOf("\"workers\"");
        if (workersIndex < 0) {
            return List.of();
        }
        int arrayStart = json.indexOf('[', workersIndex);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            return List.of();
        }
        List<String> workerIds = new ArrayList<>();
        for (String rawValue : json.substring(arrayStart + 1, arrayEnd).split(",")) {
            String value = rawValue.trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                workerIds.add(value.substring(1, value.length() - 1));
            }
        }
        return List.copyOf(workerIds);
    }

    private static String testShardNextStep(Optional<String> member, ShardEvidenceManifest manifest, Path commandReportsDir) {
        String workspace = member.isPresent() ? " --workspace" : "";
        return "Run `zolt test"
                + workspace
                + " --suite "
                + shellArgument(manifest.suiteName())
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

    private static String coverageShardNextStep(
            Optional<String> member,
            ShardEvidenceManifest manifest,
            Path commandCoverageDir) {
        String workspace = member.isPresent() ? " --workspace" : "";
        return "Run `zolt coverage"
                + workspace
                + " --suite "
                + shellArgument(manifest.suiteName())
                + " --shard "
                + manifest.index()
                + "/"
                + manifest.total()
                + "`, then rerun `zolt check --context ci --coverage-dir "
                + commandCoverageDir
                + "`.";
    }

    private record CoverageReportCounts(long xmlReports, long htmlReports) {
    }

    private static Optional<String> stringField(String json, String name) {
        int fieldIndex = json.indexOf("\"" + name + "\"");
        if (fieldIndex < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', fieldIndex);
        int valueStart = json.indexOf('"', colon + 1);
        if (colon < 0 || valueStart < 0) {
            return Optional.empty();
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = valueStart + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                switch (character) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> value.append(character);
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return Optional.of(value.toString());
            } else {
                value.append(character);
            }
        }
        return Optional.empty();
    }

    private static String shellArgument(String value) {
        if (value != null && value.matches("[A-Za-z0-9._-]+")) {
            return value;
        }
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record ShardEvidenceManifest(
            String suiteName,
            String suiteSegment,
            String shardSegment,
            int index,
            int total,
            boolean empty) {
        String displayName() {
            return suiteName + "/" + shardSegment;
        }
    }

    private record ShardNumbers(int index, int total) {
    }
}
