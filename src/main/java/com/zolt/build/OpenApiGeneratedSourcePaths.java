package com.zolt.build;

import java.nio.file.Path;

final class OpenApiGeneratedSourcePaths {
    private OpenApiGeneratedSourcePaths() {
    }

    static Path safeProjectPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        Path configured = Path.of(configuredPath);
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !path.startsWith(projectRoot) || path.equals(projectRoot)) {
            throw new BuildException(
                    "Invalid OpenAPI "
                            + field
                            + " path `"
                            + configuredPath
                            + "` for [generated."
                            + scope
                            + "."
                            + id
                            + "]. Use a project-relative path under the project directory.");
        }
        return path;
    }
}
