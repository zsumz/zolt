package com.zolt.project.init;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;

@FunctionalInterface
public interface ProjectConfigWriter {
    void write(Path path, ProjectConfig config);
}
