package com.zolt.build;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;
import java.util.Optional;

public record TestProfileSettings(boolean enabled, Optional<Path> profileDirectory) {
    private static final Path DEFAULT_PROFILE_DIRECTORY = Path.of("target/test-profile");

    public TestProfileSettings {
        profileDirectory = profileDirectory == null ? Optional.empty() : profileDirectory;
    }

    public static TestProfileSettings disabled() {
        return new TestProfileSettings(false, Optional.empty());
    }

    public static TestProfileSettings fromCli(boolean enabled, Path profileDirectory) {
        if (!enabled && profileDirectory == null) {
            return disabled();
        }
        return new TestProfileSettings(enabled || profileDirectory != null, Optional.ofNullable(profileDirectory));
    }

    public Optional<Path> absoluteProfileDirectory(Path projectDirectory) {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.of(safeProfileDirectory(projectDirectory, profileDirectory.orElse(DEFAULT_PROFILE_DIRECTORY)));
    }

    private static Path safeProfileDirectory(Path projectDirectory, Path profileDirectory) {
        try {
            return ProjectPaths.output(
                    ProjectPaths.root(projectDirectory),
                    "--profile-dir",
                    profileDirectory.toString());
        } catch (ProjectPathException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }
}
