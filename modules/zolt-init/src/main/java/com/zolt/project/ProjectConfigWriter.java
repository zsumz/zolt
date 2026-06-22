package com.zolt.project;

import java.nio.file.Path;

@FunctionalInterface
public interface ProjectConfigWriter {
    void write(Path path, ProjectConfig config);
}
