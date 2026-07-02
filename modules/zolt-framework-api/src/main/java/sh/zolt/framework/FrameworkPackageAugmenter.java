package sh.zolt.framework;

import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface FrameworkPackageAugmenter {
    Optional<FrameworkPackageResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot);

    default String missingPackageResultMessage(PackageMode mode) {
        return "Framework package mode `"
                + mode.configValue()
                + "` requires a matching framework adapter. Enable the framework in zolt.toml, run `zolt resolve`, then retry.";
    }

    default String missingRunnerJarMessage(PackageMode mode, Path runnerJar) {
        return "Framework package mode `"
                + mode.configValue()
                + "` expected a runner jar at "
                + runnerJar
                + ". Run `zolt build` and check the framework package output.";
    }

    default String inspectPackageDirectoryMessage(PackageMode mode, Path packageDirectory) {
        return "Could not inspect framework package directory at "
                + packageDirectory
                + ". Check that the package output is readable and retry.";
    }

    static FrameworkPackageAugmenter none() {
        return (projectDirectory, config, cacheRoot) -> Optional.empty();
    }
}
