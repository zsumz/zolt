package com.zolt.workspace.service;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;

public record WorkspaceMember(
        String path,
        Path directory,
        ProjectConfig config) {
}
