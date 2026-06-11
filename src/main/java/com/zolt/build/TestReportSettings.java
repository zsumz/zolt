package com.zolt.build;

import java.nio.file.Path;
import java.util.Optional;

public record TestReportSettings(Optional<Path> reportsDirectory) {
    public TestReportSettings {
        reportsDirectory = reportsDirectory == null ? Optional.empty() : reportsDirectory;
        reportsDirectory.ifPresent(directory -> {
            if (directory.isAbsolute()) {
                throw new TestRunException(
                        "Test reports directory must be project-relative. Use a path such as `target/test-reports`.");
            }
            if (directory.normalize().startsWith("..")) {
                throw new TestRunException(
                        "Test reports directory must stay inside the project. Use a path such as `target/test-reports`.");
            }
        });
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

    public Optional<Path> absoluteReportsDirectory(Path projectDirectory) {
        return reportsDirectory.map(directory -> projectDirectory.toAbsolutePath().normalize()
                .resolve(directory)
                .normalize());
    }
}
