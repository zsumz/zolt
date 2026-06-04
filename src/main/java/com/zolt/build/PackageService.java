package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

public final class PackageService {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;

    private final BuildService buildService;
    private final ManifestGenerator manifestGenerator;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;

    public PackageService() {
        this(new BuildService(), new ManifestGenerator(), new ZoltLockfileReader(), new ClasspathBuilder());
    }

    PackageService(
            BuildService buildService,
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
        this.buildService = buildService;
        this.manifestGenerator = manifestGenerator;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
    }

    public PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        BuildResult buildResult = buildService.build(projectDirectory, config, cacheRoot);
        return packageJar(projectDirectory, config, buildResult, cacheRoot);
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot) {
        return packageJar(projectDirectory, config, buildResult, Optional.of(cacheRoot));
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult) {
        return packageJar(projectDirectory, config, buildResult, Optional.empty());
    }

    private PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<Path> cacheRoot) {
        Path outputDirectory = buildResult.outputDirectory();
        if (!Files.isDirectory(outputDirectory)) {
            throw new PackageException(
                    "Build output directory does not exist at "
                            + outputDirectory
                            + ". Run zolt build and check [build].output in zolt.toml.");
        }

        Path jarPath = projectDirectory
                .resolve("target")
                .resolve(config.project().name() + "-" + config.project().version() + ".jar");
        Path runtimeClasspathPath = runtimeClasspathPath(jarPath);
        GeneratedManifest manifest = manifestGenerator.generate(config);

        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (OutputStream fileOutput = Files.newOutputStream(jarPath);
                    JarOutputStream jarOutput = new JarOutputStream(fileOutput)) {
                writeEntry(jarOutput, manifest.path(), manifest.content());
                for (Path file : files) {
                    writeEntry(jarOutput, entryName(outputDirectory, file), Files.readAllBytes(file));
                }
            }
            Optional<Path> writtenRuntimeClasspathPath = Optional.empty();
            if (cacheRoot.isPresent()) {
                writeRuntimeClasspath(projectDirectory, cacheRoot.orElseThrow(), runtimeClasspathPath);
                writtenRuntimeClasspathPath = Optional.of(runtimeClasspathPath);
            }
            return new PackageResult(
                    buildResult,
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

    private void writeRuntimeClasspath(
            Path projectDirectory,
            Path cacheRoot,
            Path runtimeClasspathPath) throws IOException {
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        String content = classpaths.runtime().entries().stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
        if (!content.isEmpty()) {
            content = content + "\n";
        }
        Files.writeString(runtimeClasspathPath, content);
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
                    .sorted(Comparator.comparing(path -> entryName(outputDirectory, path)))
                    .toList();
        }
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] content) throws IOException {
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            output.putNextEntry(entry);
            output.write(content);
            output.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Remove or rename the duplicate resource and try packaging again.",
                    exception);
        }
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }
}
