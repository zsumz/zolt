package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import sh.zolt.project.ProjectPathException;
import sh.zolt.project.ProjectPaths;
import java.nio.file.Path;

/** Real-path-contained input/output resolution for exec steps, mirroring the OpenAPI path helper. */
final class ExecGeneratedSourcePaths {
    private ExecGeneratedSourcePaths() {
    }

    static Path inputPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        try {
            return ProjectPaths.input(projectRoot, key(scope, id, field), configuredPath);
        } catch (ProjectPathException exception) {
            throw invalid(configuredPath, scope, id, field, exception);
        }
    }

    static Path outputPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        try {
            return ProjectPaths.output(projectRoot, key(scope, id, field), configuredPath);
        } catch (ProjectPathException exception) {
            throw invalid(configuredPath, scope, id, field, exception);
        }
    }

    private static BuildException invalid(
            String configuredPath,
            String scope,
            String id,
            String field,
            ProjectPathException exception) {
        return new BuildException(
                "Invalid exec " + field + " path `" + configuredPath + "` for [generated." + scope + "." + id + "]. "
                        + exception.getMessage(),
                exception);
    }

    private static String key(String scope, String id, String field) {
        return "[generated." + scope + "." + id + "]." + field;
    }
}
