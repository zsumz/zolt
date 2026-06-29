package com.zolt.project;

import java.nio.file.Path;

@FunctionalInterface
public interface WorkspaceConfigWriter {
    void write(Path path, WorkspaceInitConfig config);
}
