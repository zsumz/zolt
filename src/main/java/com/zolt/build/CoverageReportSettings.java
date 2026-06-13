package com.zolt.build;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
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
        return new CoverageReportSettings(
                true,
                true,
                DEFAULT_ROOT.resolve("jacoco.exec"),
                DEFAULT_ROOT.resolve("jacoco.xml"),
                DEFAULT_ROOT.resolve("html"),
                TestReportSettings.reportsDirectory(DEFAULT_ROOT.resolve("test-reports")));
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
