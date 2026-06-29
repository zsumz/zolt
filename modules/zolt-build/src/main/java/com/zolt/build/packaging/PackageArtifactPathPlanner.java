package com.zolt.build.packaging;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;

public final class PackageArtifactPathPlanner {
    public Path jarPath(Path projectDirectory, ProjectConfig config) {
        return archivePath(projectDirectory, config, "jar");
    }

    public Path archivePath(Path projectDirectory, ProjectConfig config, String extension) {
        return ProjectPaths.output(
                projectDirectory,
                "package archive",
                config.build().outputRoot() + "/" + artifactBaseName(config) + "." + extension);
    }

    public String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }
}
