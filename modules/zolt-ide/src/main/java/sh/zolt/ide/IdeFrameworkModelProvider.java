package sh.zolt.ide;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface IdeFrameworkModelProvider {
    IdeModel.FrameworkInfo build(
            Path root,
            Path cacheRoot,
            ProjectConfig config,
            List<IdeModel.Diagnostic> diagnostics);

    static IdeFrameworkModelProvider none() {
        return (root, cacheRoot, config, diagnostics) -> new IdeModel.FrameworkInfo(new IdeModel.QuarkusInfo(
                false,
                null,
                "disabled",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()));
    }
}
