package sh.zolt.build.packaging.layout;

import sh.zolt.build.BuildResult;
import sh.zolt.build.PackageException;
import sh.zolt.build.packaging.PackageResult;
import sh.zolt.framework.FrameworkPackageResult;
import sh.zolt.project.PackageMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class QuarkusFastJarLayoutAssembler {
    public PackageResult assemble(
            BuildResult buildResult,
            PackageMode expectedMode,
            FrameworkPackageResult packageResult,
            String missingRunnerJarMessage,
            String inspectPackageDirectoryMessage) {
        Path runnerJar = packageResult.runnerJar();
        if (!Files.isRegularFile(runnerJar)) {
            throw new PackageException(missingRunnerJarMessage);
        }
        if (packageResult.mode() != expectedMode) {
            throw new PackageException(
                    "Framework package mode `"
                            + expectedMode.configValue()
                            + "` returned package mode `"
                            + packageResult.mode().configValue()
                            + "`. Check the framework package adapter.");
        }
        try {
            return new PackageResult(
                    buildResult,
                    packageResult.mode(),
                    runnerJar,
                    Optional.empty(),
                    packageFiles(packageResult.packageDirectory()).size(),
                    true)
                    .withApplicationLayout(packageResult.applicationLayout());
        } catch (IOException exception) {
            throw new PackageException(inspectPackageDirectoryMessage, exception);
        }
    }

    private static List<Path> packageFiles(Path packageDirectory) throws IOException {
        try (var stream = Files.walk(packageDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> entryName(packageDirectory, path)))
                    .toList();
        }
    }

    private static String entryName(Path packageDirectory, Path file) {
        return packageDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }
}
