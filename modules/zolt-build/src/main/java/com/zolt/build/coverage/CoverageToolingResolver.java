package com.zolt.build.coverage;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;

@FunctionalInterface
interface CoverageToolingResolver {
    void resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot);
}
