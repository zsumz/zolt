package com.zolt.build;

import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.test.TestShardSpec;
import com.zolt.test.TestSuitePathSegments;
import java.nio.file.Path;
import java.util.Optional;

public record CoverageReportSettings(
        boolean xml,
        boolean html,
        Path execFile,
        Path xmlReport,
        Path htmlDirectory,
        TestReportSettings testReports) {
    private static final Path DEFAULT_ROOT = Path.of("target/coverage");

    public CoverageReportSettings {
        execFile = execFile == null ? DEFAULT_ROOT.resolve("jacoco.exec") : execFile;
        xmlReport = xmlReport == null ? DEFAULT_ROOT.resolve("jacoco.xml") : xmlReport;
        htmlDirectory = htmlDirectory == null ? DEFAULT_ROOT.resolve("html") : htmlDirectory;
        testReports = testReports == null ? TestReportSettings.reportsDirectory(DEFAULT_ROOT.resolve("test-reports")) : testReports;
        validateProjectRelative("coverage exec file", execFile, "target/coverage/jacoco.exec");
        validateProjectRelative("coverage XML report", xmlReport, "target/coverage/jacoco.xml");
        validateProjectRelative("coverage HTML directory", htmlDirectory, "target/coverage/html");
        if (!xml && !html) {
            throw new CoverageException("Coverage requires at least one report format. Enable XML or HTML reports.");
        }
    }

    public static CoverageReportSettings defaults() {
        return defaultsForOutputRoot(Path.of("target"));
    }

    public static CoverageReportSettings defaultsForOutputRoot(String outputRoot) {
        return defaultsForOutputRoot(Path.of(outputRoot));
    }

    public static CoverageReportSettings defaultsForOutputRoot(Path outputRoot) {
        return forOutputRoot(true, true, outputRoot, null, null, null, null);
    }

    public static CoverageReportSettings forOutputRoot(
            boolean xml,
            boolean html,
            Path outputRoot,
            Path execFile,
            Path xmlReport,
            Path htmlDirectory,
            Path reportsDirectory) {
        Path coverageRoot = (outputRoot == null ? Path.of("target") : outputRoot).resolve("coverage");
        return new CoverageReportSettings(
                xml,
                html,
                execFile == null ? coverageRoot.resolve("jacoco.exec") : execFile,
                xmlReport == null ? coverageRoot.resolve("jacoco.xml") : xmlReport,
                htmlDirectory == null ? coverageRoot.resolve("html") : htmlDirectory,
                TestReportSettings.reportsDirectory(
                        reportsDirectory == null ? coverageRoot.resolve("test-reports") : reportsDirectory));
    }

    public CoverageReportSettings forShard(String suiteName, TestShardSpec shard) {
        if (shard == null) {
            return this;
        }
        String suiteSegment = TestSuitePathSegments.suiteSegment(suiteName);
        String shardSegment = TestSuitePathSegments.shardSegment(shard);
        Path shardRoot = execFile.getParent() == null
                ? Path.of("shards").resolve(suiteSegment).resolve(shardSegment)
                : execFile.getParent().resolve("shards").resolve(suiteSegment).resolve(shardSegment);
        return new CoverageReportSettings(
                xml,
                html,
                shardRoot.resolve(execFile.getFileName()),
                xmlReport.getParent() == null
                        ? shardRoot.resolve(xmlReport.getFileName())
                        : xmlReport.getParent().resolve("shards").resolve(suiteSegment).resolve(shardSegment).resolve(xmlReport.getFileName()),
                htmlDirectory.getParent() == null
                        ? shardRoot.resolve(htmlDirectory.getFileName())
                        : htmlDirectory.getParent().resolve("shards").resolve(suiteSegment).resolve(shardSegment).resolve(htmlDirectory.getFileName()),
                testReports);
    }

    public Optional<Path> absoluteXmlReport(Path projectDirectory) {
        return xml ? Optional.of(absolute(projectDirectory, xmlReport)) : Optional.empty();
    }

    public Optional<Path> absoluteHtmlDirectory(Path projectDirectory) {
        return html ? Optional.of(absolute(projectDirectory, htmlDirectory)) : Optional.empty();
    }

    public Path absoluteExecFile(Path projectDirectory) {
        return absolute(projectDirectory, execFile);
    }

    private static Path absolute(Path projectDirectory, Path path) {
        try {
            return ProjectPaths.output(ProjectPaths.root(projectDirectory), "coverage output", path.toString());
        } catch (ProjectPathException exception) {
            throw new CoverageException(exception.getMessage(), exception);
        }
    }

    private static void validateProjectRelative(String label, Path path, String example) {
        if (path.isAbsolute()) {
            throw new CoverageException("The " + label + " must be project-relative. Use a path such as `" + example + "`.");
        }
        if (path.normalize().startsWith("..")) {
            throw new CoverageException("The " + label + " must stay inside the project. Use a path such as `" + example + "`.");
        }
    }

}
