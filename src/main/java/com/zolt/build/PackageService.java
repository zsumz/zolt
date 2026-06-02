package com.zolt.build;

import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class PackageService {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;

    private final BuildService buildService;
    private final ManifestGenerator manifestGenerator;

    public PackageService() {
        this(new BuildService(), new ManifestGenerator());
    }

    PackageService(BuildService buildService, ManifestGenerator manifestGenerator) {
        this.buildService = buildService;
        this.manifestGenerator = manifestGenerator;
    }

    public PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        BuildResult buildResult = buildService.build(projectDirectory, config, cacheRoot);
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
            return new PackageResult(buildResult, jarPath, files.size(), manifest.mainClass().isPresent());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
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
        JarEntry entry = new JarEntry(name);
        entry.setTime(DETERMINISTIC_ENTRY_TIME);
        output.putNextEntry(entry);
        output.write(content);
        output.closeEntry();
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }
}
