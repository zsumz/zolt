package com.zolt.build;

import com.zolt.classpath.Classpath;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class BuildFingerprintContent {
    private static final String VERSION = "1";
    private static final List<String> OUTPUT_DIRECTORY_NAMES = List.of("build", "target");
    private static final List<String> LOCAL_FINGERPRINT_FILES = List.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-test.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint.state");

    private final BuildFingerprintExpectedClasses expectedClasses = new BuildFingerprintExpectedClasses();

    String fingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            String resourceRootKey,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "projectConfig", sha256(config.toString().getBytes(StandardCharsets.UTF_8)));
        line(content, "zoltToml", fileHash(projectRoot.resolve("zolt.toml"), cachedState, collectedState));
        line(content, "lockfile", fileHash(lockfilePath, cachedState, collectedState));
        section(content, "sourceRoots", sourceRoots.stream().map(BuildFingerprintContent::normalize).toList());
        line(content, "outputDirectory", normalize(outputDirectoryName));
        line(content, "generatedSourcesDirectory", relative(projectRoot, generatedSourcesDirectory));
        line(content, "compilerSettings", config.compilerSettings().toString());
        section(content, "compileClasspath", classpathEntries(compileClasspath, cachedState, collectedState));
        section(content, "processorClasspath", classpathEntries(processorClasspath, cachedState, collectedState));
        section(content, "sources", fileEntries(projectRoot, sources, cachedState, collectedState));
        section(content, "generatedSourceInputs", generatedSourceInputEntries(projectRoot, generatedSteps, cachedState, collectedState));
        section(content, "resources", resourceEntries(projectRoot, resourceRoots, resourceRootKey, config.build(), cachedState, collectedState));
        section(content, "generatedSources", generatedSourceEntries(projectRoot, generatedSourcesDirectory, cachedState, collectedState));
        section(content, "expectedClasses", expectedClasses.entries(projectRoot, sourceRoots, sources, outputDirectory));
        return content.toString();
    }

    private static List<String> classpathEntries(
            Classpath classpath,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> path + "|" + classpathHash(path, cachedState, collectedState))
                .toList();
    }

    private static String classpathHash(
            Path path,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            Optional<String> abiHash = zoltOutputAbiHash(normalized);
            if (abiHash.isPresent()) {
                return "abi:" + abiHash.orElseThrow();
            }
        }
        return fileHash(normalized, cachedState, collectedState);
    }

    private static Optional<String> zoltOutputAbiHash(Path directory) {
        Optional<IncrementalCompileState> state = new IncrementalCompileStateCodec()
                .read(IncrementalCompileState.mainStatePath(directory));
        if (state.isEmpty() || !state.orElseThrow().outputDirectory().equals(directory)) {
            return Optional.empty();
        }
        StringBuilder content = new StringBuilder();
        for (IncrementalCompileState.ClassRecord classRecord : state.orElseThrow().classes()) {
            content.append(classRecord.binaryName())
                    .append('|')
                    .append(classRecord.abiHash())
                    .append('|')
                    .append(classRecord.packagePrivateAbiHash())
                    .append('\n');
        }
        return Optional.of(sha256(content.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> fileEntries(
            Path projectRoot,
            List<Path> files,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return files.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> relative(projectRoot, path) + "|" + fileHash(path, cachedState, collectedState))
                .toList();
    }

    private static List<String> resourceEntries(
            Path projectRoot,
            List<String> configuredRoots,
            String resourceRootKey,
            BuildSettings settings,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        List<Path> resources = new ArrayList<>();
        Path mainOutput = outputPath(projectRoot, "[build].output", settings.output());
        Path testOutput = outputPath(projectRoot, "[build].testOutput", settings.testOutput());
        for (String configuredRoot : configuredRoots) {
            Path root = existingRoot(projectRoot, resourceRootKey, configuredRoot);
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
        return fileEntries(projectRoot, resources, cachedState, collectedState);
    }

    private static List<String> generatedSourceInputEntries(
            Path projectRoot,
            List<GeneratedSourceStep> steps,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return steps.stream()
                .flatMap(step -> step.inputs().stream()
                        .map(input -> inputPath(projectRoot, "[generated." + step.id() + "].inputs", input)))
                .distinct()
                .sorted()
                .map(path -> relative(projectRoot, path) + "|" + fileHash(path, cachedState, collectedState))
                .toList();
    }

    private static List<String> generatedSourceEntries(
            Path projectRoot,
            Path generatedSourcesDirectory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        if (!Files.isDirectory(generatedSourcesDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(generatedSourcesDirectory)) {
            return fileEntries(
                    projectRoot,
                    paths.filter(Files::isRegularFile)
                            .map(Path::normalize)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .toList(),
                    cachedState,
                    collectedState);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint generated sources under "
                            + generatedSourcesDirectory
                            + ". Check that the directory is readable.",
                    exception);
        }
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

    private static Path inputPath(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.input(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static Path existingRoot(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.existingRoot(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static Path outputPath(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private static String fileHash(
            Path path,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return directoryHash(normalized, cachedState, collectedState);
        }
        if (!Files.isRegularFile(normalized)) {
            return "missing";
        }
        if (cachedState != null) {
            return cachedState.hashIfCurrent(normalized)
                    .orElseThrow(BuildFingerprintStateMiss::new);
        }
        try {
            String hash = sha256(Files.readAllBytes(normalized));
            if (collectedState != null) {
                collectedState.put(normalized, BuildFingerprintCachedFileHash.read(normalized, hash));
            }
            return hash;
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint file "
                            + normalized
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String directoryHash(
            Path directory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        try (Stream<Path> paths = Files.walk(directory)) {
            StringBuilder content = new StringBuilder();
            paths.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .filter(path -> !LOCAL_FINGERPRINT_FILES.contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> content
                            .append(directory.relativize(path).toString().replace('\\', '/'))
                            .append('|')
                            .append(fileHash(path, cachedState, collectedState))
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
