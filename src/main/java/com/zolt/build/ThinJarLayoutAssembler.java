package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.LockfileClasspathPackageConverter;
import com.zolt.resolve.ResolvedClasspathPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ThinJarLayoutAssembler {
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    private final ManifestGenerator manifestGenerator;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;

    ThinJarLayoutAssembler(
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
        this.manifestGenerator = manifestGenerator;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
    }

    PackageResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path jarPath,
            Optional<Path> cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path runtimeClasspathPath = runtimeClasspathPath(jarPath);
        GeneratedManifest manifest = manifestGenerator.generate(config);

        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(jarPath)) {
                archive.writeEntry(manifest.path(), manifest.content());
                for (Path file : files) {
                    archive.writeFile(entryName(outputDirectory, file), file);
                }
            }
            Optional<Path> writtenRuntimeClasspathPath = Optional.empty();
            if (cacheRoot.isPresent()) {
                if (classpathPackages.isPresent()) {
                    writeRuntimeClasspath(runtimeClasspathPath, classpathPackages.orElseThrow());
                } else {
                    writeRuntimeClasspath(projectDirectory, cacheRoot.orElseThrow(), runtimeClasspathPath);
                }
                writtenRuntimeClasspathPath = Optional.of(runtimeClasspathPath);
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.THIN,
                    jarPath,
                    writtenRuntimeClasspathPath,
                    files.size(),
                    manifest.mainClass().isPresent());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private Path requireOutputDirectory(BuildResult buildResult) {
        Path outputDirectory = buildResult.outputDirectory();
        if (!Files.isDirectory(outputDirectory)) {
            throw new PackageException(
                    "Build output directory does not exist at "
                            + outputDirectory
                            + ". Run zolt build and check [build].output in zolt.toml.");
        }
        return outputDirectory;
    }

    private void writeRuntimeClasspath(
            Path projectDirectory,
            Path cacheRoot,
            Path runtimeClasspathPath) throws IOException {
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        writeRuntimeClasspath(runtimeClasspathPath, packagedClasspathPackages(lockfile, cacheRoot));
    }

    private void writeRuntimeClasspath(
            Path runtimeClasspathPath,
            List<ResolvedClasspathPackage> classpathPackages) throws IOException {
        ClasspathSet classpaths = classpathBuilder.build(packagedClasspathPackages(classpathPackages));
        String content = classpaths.runtime().entries().stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
        if (!content.isEmpty()) {
            content = content + "\n";
        }
        Files.writeString(runtimeClasspathPath, content);
    }

    private List<ResolvedClasspathPackage> packagedClasspathPackages(ZoltLockfile lockfile, Path cacheRoot) {
        return packagedClasspathPackages(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
    }

    private static List<ResolvedClasspathPackage> packagedClasspathPackages(
            List<ResolvedClasspathPackage> classpathPackages) {
        return classpathPackages.stream()
                .filter(dependency -> dependency.scope().packagedByDefault())
                .toList();
    }

    private static Path runtimeClasspathPath(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        if (fileName.endsWith(".jar")) {
            return jarPath.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".runtime-classpath");
        }
        return jarPath.resolveSibling(fileName + ".runtime-classpath");
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
