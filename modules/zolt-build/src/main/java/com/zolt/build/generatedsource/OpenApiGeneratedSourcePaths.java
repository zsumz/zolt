package com.zolt.build.generatedsource;

import com.zolt.build.BuildException;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;

final class OpenApiGeneratedSourcePaths {
    private OpenApiGeneratedSourcePaths() {
    }

    static Path inputPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        try {
            return ProjectPaths.input(projectRoot, key(scope, id, field), configuredPath);
        } catch (ProjectPathException exception) {
            throw invalidOpenApiPath(configuredPath, scope, id, field, exception);
        }
    }

    static Path outputPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        try {
            return ProjectPaths.output(projectRoot, key(scope, id, field), configuredPath);
        } catch (ProjectPathException exception) {
            throw invalidOpenApiPath(configuredPath, scope, id, field, exception);
        }
    }

    private static BuildException invalidOpenApiPath(
            String configuredPath,
            String scope,
            String id,
            String field,
            ProjectPathException exception) {
        return new BuildException(
                "Invalid OpenAPI "
                        + field
                        + " path `"
                        + configuredPath
                        + "` for [generated."
                        + scope
                        + "."
                        + id
                        + "]. "
                        + exception.getMessage(),
                exception);
    }

    private static String key(String scope, String id, String field) {
        return "[generated." + scope + "." + id + "]." + field;
    }
}
