package sh.zolt.project.init;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;

@FunctionalInterface
public interface ProjectConfigWriter {
    void write(Path path, ProjectConfig config);
}
