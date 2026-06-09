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
    private static final String FILE_NAME = ".zolt-build-main.fingerprint";
    private static final List<String> OUTPUT_DIRECTORY_NAMES = List.of("build", "target");

    public boolean isMainCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        Path fingerprintPath = fingerprintPath(outputDirectory);
        if (!Files.isRegularFile(fingerprintPath)) {
            return false;
        }
        if (!Files.isDirectory(outputDirectory)) {
            return false;
        }
        if (!expectedClassFilesPresent(projectDirectory, config.build(), sources.mainSources(), outputDirectory)) {
            return false;
        }
        if (!classpaths.processor().entries().isEmpty() && !Files.isDirectory(generatedSourcesDirectory)) {
            return false;
        }
        try {
            return Files.readString(fingerprintPath).equals(fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sources,
                    classpaths,
                    outputDirectory,
                    generatedSourcesDirectory));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read build fingerprint at "
                            + fingerprintPath
                            + ". Delete the file or rerun `zolt build` to refresh it.",
                    exception);
        }
    }

    public void writeMainCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        Path fingerprintPath = fingerprintPath(outputDirectory);
        try {
            Files.createDirectories(fingerprintPath.getParent());
            Files.writeString(
                    fingerprintPath,
                    fingerprint(
                            projectDirectory,
                            config,
                            lockfilePath,
                            sources,
                            classpaths,
                            outputDirectory,
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

    private static Path fingerprintPath(Path outputDirectory) {
        return outputDirectory.resolve(FILE_NAME);
    }

    private String fingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "projectConfig", sha256(config.toString().getBytes(StandardCharsets.UTF_8)));
        line(content, "zoltToml", fileHash(projectRoot.resolve("zolt.toml")));
        line(content, "lockfile", fileHash(lockfilePath));
        line(content, "sourceRoot", normalize(config.build().source()));
        line(content, "outputDirectory", normalize(config.build().output()));
        line(content, "generatedSourcesDirectory", normalize(config.compilerSettings().generatedSources()));
        line(content, "compilerSettings", config.compilerSettings().toString());
        section(content, "compileClasspath", classpathEntries(classpaths.compile()));
        section(content, "processorClasspath", classpathEntries(classpaths.processor()));
        section(content, "mainSources", fileEntries(projectRoot, sources.mainSources()));
        section(content, "mainResources", resourceEntries(projectRoot, config.build().resourceRoots(), config.build()));
        section(content, "generatedSources", generatedSourceEntries(projectRoot, generatedSourcesDirectory));
        section(content, "expectedClasses", expectedClassEntries(projectRoot, config.build(), sources.mainSources(), outputDirectory));
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
            BuildSettings settings,
            List<Path> sources,
            Path outputDirectory) {
        return expectedClassFiles(projectRoot, settings, sources, outputDirectory).stream()
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    private static boolean expectedClassFilesPresent(
            Path projectDirectory,
            BuildSettings settings,
            List<Path> sources,
            Path outputDirectory) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        for (Path expectedClass : expectedClassFiles(projectRoot, settings, sources, outputDirectory)) {
            if (!Files.isRegularFile(expectedClass)) {
                return false;
            }
        }
        return true;
    }

    private static List<Path> expectedClassFiles(
            Path projectRoot,
            BuildSettings settings,
            List<Path> sources,
            Path outputDirectory) {
        Path sourceRoot = projectRoot.resolve(settings.source()).normalize();
        return sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> path.startsWith(sourceRoot))
                .map(path -> classFile(sourceRoot, path, outputDirectory))
                .flatMap(Optional::stream)
                .sorted()
                .toList();
    }

    private static Optional<Path> classFile(Path sourceRoot, Path source, Path outputDirectory) {
        Path relative = sourceRoot.relativize(source);
        String fileName = relative.getFileName().toString();
        if (!fileName.endsWith(".java")) {
            return Optional.empty();
        }
        Path classRelative = relative.getParent() == null
                ? Path.of(fileName.substring(0, fileName.length() - ".java".length()) + ".class")
                : relative.getParent().resolve(fileName.substring(0, fileName.length() - ".java".length()) + ".class");
        return Optional.of(outputDirectory.resolve(classRelative).normalize());
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
