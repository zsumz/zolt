package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class BuildFingerprintService {
    private static final String VERSION = "1";
    private static final String MAIN_FILE_NAME = ".zolt-build-main.fingerprint";
    private static final String TEST_FILE_NAME = ".zolt-build-test.fingerprint";
    private static final List<String> OUTPUT_DIRECTORY_NAMES = List.of("build", "target");
    private static final List<String> LOCAL_FINGERPRINT_FILES = List.of(MAIN_FILE_NAME, TEST_FILE_NAME);

    public boolean isMainCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        return isCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                List.of(config.build().source()),
                config.build().resourceRoots(),
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                config.build().output(),
                generatedSourcesDirectory,
                MAIN_FILE_NAME);
    }

    public void writeMainCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        writeCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                List.of(config.build().source()),
                config.build().resourceRoots(),
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                config.build().output(),
                generatedSourcesDirectory,
                MAIN_FILE_NAME);
    }

    public boolean isTestCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        return isCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                testSourceRoots(config.build()),
                config.build().testResourceRoots(),
                sources.allTestSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                config.build().testOutput(),
                generatedSourcesDirectory,
                TEST_FILE_NAME);
    }

    public void writeTestCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        writeCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                testSourceRoots(config.build()),
                config.build().testResourceRoots(),
                sources.allTestSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                config.build().testOutput(),
                generatedSourcesDirectory,
                TEST_FILE_NAME);
    }

    private static List<String> testSourceRoots(BuildSettings settings) {
        List<String> roots = new ArrayList<>();
        roots.addAll(settings.testSources());
        roots.addAll(settings.groovyTestSources());
        return List.copyOf(roots);
    }

    private boolean isCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            List<Path> sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            String fileName) {
        Path fingerprintPath = fingerprintPath(outputDirectory, fileName);
        if (!Files.isRegularFile(fingerprintPath)) {
            return false;
        }
        if (!Files.isDirectory(outputDirectory)) {
            return false;
        }
        if (!expectedClassFilesPresent(projectDirectory, sourceRoots, sources, outputDirectory)) {
            return false;
        }
        if (!processorClasspath.entries().isEmpty() && !Files.isDirectory(generatedSourcesDirectory)) {
            return false;
        }
        try {
            return Files.readString(fingerprintPath).equals(fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sourceRoots,
                    resourceRoots,
                    sources,
                    compileClasspath,
                    processorClasspath,
                    outputDirectory,
                    outputDirectoryName,
                    generatedSourcesDirectory));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read build fingerprint at "
                            + fingerprintPath
                            + ". Delete the file or rerun `zolt build` to refresh it.",
                    exception);
        }
    }

    private void writeCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            List<Path> sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            String fileName) {
        Path fingerprintPath = fingerprintPath(outputDirectory, fileName);
        try {
            Files.createDirectories(fingerprintPath.getParent());
            Files.writeString(
                    fingerprintPath,
                    fingerprint(
                            projectDirectory,
                            config,
                            lockfilePath,
                            sourceRoots,
                            resourceRoots,
                            sources,
                            compileClasspath,
                            processorClasspath,
                            outputDirectory,
                            outputDirectoryName,
                            generatedSourcesDirectory),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write build fingerprint at "
                            + fingerprintPath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    private static Path fingerprintPath(Path outputDirectory, String fileName) {
        return outputDirectory.resolve(fileName);
    }

    private String fingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            List<Path> sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "projectConfig", sha256(config.toString().getBytes(StandardCharsets.UTF_8)));
        line(content, "zoltToml", fileHash(projectRoot.resolve("zolt.toml")));
        line(content, "lockfile", fileHash(lockfilePath));
        section(content, "sourceRoots", sourceRoots.stream().map(BuildFingerprintService::normalize).toList());
        line(content, "outputDirectory", normalize(outputDirectoryName));
        line(content, "generatedSourcesDirectory", relative(projectRoot, generatedSourcesDirectory));
        line(content, "compilerSettings", config.compilerSettings().toString());
        section(content, "compileClasspath", classpathEntries(compileClasspath));
        section(content, "processorClasspath", classpathEntries(processorClasspath));
        section(content, "sources", fileEntries(projectRoot, sources));
        section(content, "resources", resourceEntries(projectRoot, resourceRoots, config.build()));
        section(content, "generatedSources", generatedSourceEntries(projectRoot, generatedSourcesDirectory));
        section(content, "expectedClasses", expectedClassEntries(projectRoot, sourceRoots, sources, outputDirectory));
        return content.toString();
    }

    private static List<String> classpathEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> path + "|" + fileHash(path))
                .toList();
    }

    private static List<String> fileEntries(Path projectRoot, List<Path> files) {
        return files.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> relative(projectRoot, path) + "|" + fileHash(path))
                .toList();
    }

    private static List<String> resourceEntries(Path projectRoot, List<String> configuredRoots, BuildSettings settings) {
        List<Path> resources = new ArrayList<>();
        Path mainOutput = projectRoot.resolve(settings.output()).normalize();
        Path testOutput = projectRoot.resolve(settings.testOutput()).normalize();
        for (String configuredRoot : configuredRoots) {
            Path root = projectRoot.resolve(configuredRoot).normalize();
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .map(Path::normalize)
                        .filter(path -> !path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !path.startsWith(mainOutput))
                        .filter(path -> !path.startsWith(testOutput))
                        .filter(path -> !startsWithOutputDirectorySegment(root.relativize(path)))
                        .forEach(resources::add);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not fingerprint resources under "
                                + root
                                + ". Check that the directory is readable.",
                        exception);
            }
        }
        return fileEntries(projectRoot, resources);
    }

    private static List<String> generatedSourceEntries(Path projectRoot, Path generatedSourcesDirectory) {
        if (!Files.isDirectory(generatedSourcesDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(generatedSourcesDirectory)) {
            return fileEntries(
                    projectRoot,
                    paths.filter(Files::isRegularFile)
                            .map(Path::normalize)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .toList());
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint generated sources under "
                            + generatedSourcesDirectory
                            + ". Check that the directory is readable.",
                    exception);
        }
    }

    private static List<String> expectedClassEntries(
            Path projectRoot,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        return expectedClassFiles(projectRoot, sourceRoots, sources, outputDirectory).stream()
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    private static boolean expectedClassFilesPresent(
            Path projectDirectory,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        for (Path expectedClass : expectedClassFiles(projectRoot, sourceRoots, sources, outputDirectory)) {
            if (!Files.isRegularFile(expectedClass)) {
                return false;
            }
        }
        return true;
    }

    private static List<Path> expectedClassFiles(
            Path projectRoot,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        List<Path> roots = sourceRoots.stream()
                .map(root -> projectRoot.resolve(root).normalize())
                .toList();
        return sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .map(path -> classFile(sourceRootFor(roots, path), path, outputDirectory))
                .flatMap(Optional::stream)
                .sorted()
                .toList();
    }

    private static Optional<Path> classFile(Optional<Path> sourceRoot, Path source, Path outputDirectory) {
        if (sourceRoot.isEmpty()) {
            return Optional.empty();
        }
        Path relative = sourceRoot.orElseThrow().relativize(source);
        String fileName = relative.getFileName().toString();
        String extension;
        if (fileName.endsWith(".java")) {
            extension = ".java";
        } else if (fileName.endsWith(".groovy")) {
            extension = ".groovy";
        } else {
            return Optional.empty();
        }
        Path classRelative = relative.getParent() == null
                ? Path.of(fileName.substring(0, fileName.length() - extension.length()) + ".class")
                : relative.getParent().resolve(fileName.substring(0, fileName.length() - extension.length()) + ".class");
        return Optional.of(outputDirectory.resolve(classRelative).normalize());
    }

    private static Optional<Path> sourceRootFor(List<Path> sourceRoots, Path source) {
        return sourceRoots.stream()
                .filter(source::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount));
    }

    private static void section(StringBuilder content, String name, List<String> entries) {
        content.append('[').append(name).append(']').append('\n');
        entries.stream().sorted(Comparator.naturalOrder()).forEach(entry -> content.append(entry).append('\n'));
    }

    private static void line(StringBuilder content, String name, String value) {
        content.append(name).append('=').append(value).append('\n');
    }

    private static String normalize(String value) {
        return Path.of(value).normalize().toString().replace('\\', '/');
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private static String fileHash(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return directoryHash(normalized);
        }
        if (!Files.isRegularFile(normalized)) {
            return "missing";
        }
        try {
            return sha256(Files.readAllBytes(normalized));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint file "
                            + normalized
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String directoryHash(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            StringBuilder content = new StringBuilder();
            paths.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .filter(path -> !LOCAL_FINGERPRINT_FILES.contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> content
                            .append(directory.relativize(path).toString().replace('\\', '/'))
                            .append('|')
                            .append(fileHash(path))
                            .append('\n'));
            return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint directory "
                            + directory
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new BuildException("Could not compute build fingerprint because SHA-256 is unavailable.", exception);
        }
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        return relativePath.getNameCount() > 0
                && OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
