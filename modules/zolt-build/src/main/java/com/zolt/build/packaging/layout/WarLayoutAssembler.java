package com.zolt.build.packaging.layout;

import com.zolt.build.BuildResult;
import com.zolt.build.manifest.GeneratedManifest;
import com.zolt.build.manifest.ManifestGenerator;
import com.zolt.build.PackageException;
import com.zolt.build.packaging.PackageArchiveWriter;
import com.zolt.build.packaging.PackageResult;
import com.zolt.build.packaging.PackageRuntimeJar;
import com.zolt.build.packaging.PackageRuntimeJars;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class WarLayoutAssembler {
    private static final String WEB_INF_PREFIX = "WEB-INF/";
    private static final String WEB_INF_CLASSES_PREFIX = "WEB-INF/classes/";
    private static final String WEB_INF_LIB_PREFIX = "WEB-INF/lib/";
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    private final ManifestGenerator manifestGenerator;

    public WarLayoutAssembler(ManifestGenerator manifestGenerator) {
        this.manifestGenerator = manifestGenerator;
    }

    public PackageResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path outputDirectory,
            Path warPath,
            List<PackageRuntimeJar> runtimeJars) {
        GeneratedManifest manifest = manifestGenerator.generateWithoutMain(projectDirectory, config);

        try {
            Files.createDirectories(warPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(warPath)) {
                archive.writeEntry(manifest.path(), manifest.content());
                archive.writeDirectory(WEB_INF_PREFIX);
                archive.writeDirectory(WEB_INF_CLASSES_PREFIX);
                archive.writeDirectory(WEB_INF_LIB_PREFIX);
                for (Path file : files) {
                    String warEntryName = WEB_INF_CLASSES_PREFIX + entryName(outputDirectory, file);
                    archive.writeParentDirectories(warEntryName);
                    archive.writeFile(warEntryName, file);
                }
                for (PackageRuntimeJar runtimeJar : runtimeJars) {
                    archive.writeStoredEntry(
                            WEB_INF_LIB_PREFIX + PackageRuntimeJars.nestedJarName(runtimeJar),
                            PackageRuntimeJars.read(runtimeJar));
                }
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.WAR,
                    warPath,
                    Optional.empty(),
                    files.size(),
                    false);
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package WAR at "
                            + warPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private static List<Path> compiledFiles(Path outputDirectory) throws IOException {
        try (var stream = Files.walk(outputDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !LOCAL_BUILD_FINGERPRINTS.contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> entryName(outputDirectory, path)))
                    .toList();
        }
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }

}
