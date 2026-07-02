package sh.zolt.build.coverage;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;

@FunctionalInterface
interface CoverageToolingResolver {
    void resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot);
}
