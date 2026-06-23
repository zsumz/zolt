package com.zolt.build;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;

final class PackageArtifactPathPlanner {
    Path jarPath(Path projectDirectory, ProjectConfig config) {
        return archivePath(projectDirectory, config, "jar");
    }

    Path archivePath(Path projectDirectory, ProjectConfig config, String extension) {
        return ProjectPaths.output(
                projectDirectory,
                "package archive",
                config.build().outputRoot() + "/" + artifactBaseName(config) + "." + extension);
    }

    String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }
}
