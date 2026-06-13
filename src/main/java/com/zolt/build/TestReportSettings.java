package com.zolt.build;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;
import java.util.Optional;

public record TestReportSettings(Optional<Path> reportsDirectory) {
    public TestReportSettings {
        reportsDirectory = reportsDirectory == null ? Optional.empty() : reportsDirectory;
    }

    public static TestReportSettings disabled() {
        return new TestReportSettings(Optional.empty());
    }

    public static TestReportSettings reportsDirectory(Path reportsDirectory) {
        return new TestReportSettings(Optional.ofNullable(reportsDirectory));
    }

    public TestReportSettings forWorkspaceMember(String memberPath) {
        if (reportsDirectory.isEmpty()) {
            return disabled();
        }
        return reportsDirectory(reportsDirectory.orElseThrow().resolve(memberPath));
    }

    public Optional<Path> projectRelativeReportsDirectory(Path projectDirectory) {
        reportsDirectory.ifPresent(directory -> safeReportsDirectory(projectDirectory, directory));
        return reportsDirectory;
    }

    public Optional<Path> absoluteReportsDirectory(Path projectDirectory) {
        return reportsDirectory.map(directory -> safeReportsDirectory(projectDirectory, directory));
    }

    private static Path safeReportsDirectory(Path projectDirectory, Path reportsDirectory) {
        try {
            return ProjectPaths.output(
                    ProjectPaths.root(projectDirectory),
                    "--reports-dir",
                    reportsDirectory.toString());
        } catch (ProjectPathException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }
}
