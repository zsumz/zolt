package com.zolt.project;

import com.zolt.workspace.WorkspaceConfig;
import java.nio.file.Path;

@FunctionalInterface
public interface WorkspaceConfigWriter {
    void write(Path path, WorkspaceConfig config);
}
