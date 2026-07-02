package sh.zolt.workspace.service;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;

public record WorkspaceMember(
        String path,
        Path directory,
        ProjectConfig config) {
}
