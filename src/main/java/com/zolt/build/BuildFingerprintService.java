package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class BuildFingerprintService {
    private static final String VERSION = "1";
    private static final String STATE_VERSION = "1";
    private static final String MAIN_FILE_NAME = ".zolt-build-main.fingerprint";
    private static final String TEST_FILE_NAME = ".zolt-build-test.fingerprint";
    private static final String STATE_SUFFIX = ".state";
    private static final List<String> OUTPUT_DIRECTORY_NAMES = List.of("build", "target");
    private static final List<String> LOCAL_FINGERPRINT_FILES = List.of(
            MAIN_FILE_NAME,
            TEST_FILE_NAME,
            MAIN_FILE_NAME + STATE_SUFFIX,
            TEST_FILE_NAME + STATE_SUFFIX);

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
                mainSourceRoots(config.build()),
                config.build().resourceRoots(),
                sources.mainSources(),
                config.build().generatedMainSources(),
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
                mainSourceRoots(config.build()),
                config.build().resourceRoots(),
                sources.mainSources(),
                config.build().generatedMainSources(),
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
                config.build().generatedTestSources(),
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
                config.build().generatedTestSources(),
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
        roots.addAll(settings.generatedTestSources().stream()
                .map(GeneratedSourceStep::output)
                .toList());
        roots.addAll(settings.groovyTestSources());
        return List.copyOf(roots);
    }

    private static List<String> mainSourceRoots(BuildSettings settings) {
        List<String> roots = new ArrayList<>();
        roots.add(settings.source());
        roots.addAll(settings.generatedMainSources().stream()
                .map(GeneratedSourceStep::output)
                .toList());
        return List.copyOf(roots);
    }

    private boolean isCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
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
            String existing = Files.readString(fingerprintPath);
            Optional<FingerprintState> state = readState(fingerprintPath);
            if (state.isPresent() && state.orElseThrow().matchesFingerprint(existing)) {
                try {
                    return existing.equals(fingerprint(
                            projectDirectory,
                            config,
                            lockfilePath,
                            sourceRoots,
                            resourceRoots,
                            sources,
                            generatedSteps,
                            compileClasspath,
                            processorClasspath,
                            outputDirectory,
                            outputDirectoryName,
                            generatedSourcesDirectory,
                            state.orElseThrow(),
                            null));
                } catch (FingerprintStateMiss ignored) {
                    // Fall back to the full content-hash path below.
                }
            }
            return existing.equals(fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sourceRoots,
                    resourceRoots,
                    sources,
                    generatedSteps,
                    compileClasspath,
                    processorClasspath,
                    outputDirectory,
                    outputDirectoryName,
                    generatedSourcesDirectory,
                    null,
                    null));
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
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            String fileName) {
        Path fingerprintPath = fingerprintPath(outputDirectory, fileName);
        try {
            Files.createDirectories(fingerprintPath.getParent());
            Map<Path, CachedFileHash> state = new LinkedHashMap<>();
            String content = fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sourceRoots,
                    resourceRoots,
                    sources,
                    generatedSteps,
                    compileClasspath,
                    processorClasspath,
                    outputDirectory,
                    outputDirectoryName,
                    generatedSourcesDirectory,
                    null,
                    state);
            Files.writeString(fingerprintPath, content, StandardCharsets.UTF_8);
            writeState(fingerprintPath, content, state);
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

    private static Path statePath(Path fingerprintPath) {
        return fingerprintPath.resolveSibling(fingerprintPath.getFileName() + STATE_SUFFIX);
    }

    private String fingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "projectConfig", sha256(config.toString().getBytes(StandardCharsets.UTF_8)));
        line(content, "zoltToml", fileHash(projectRoot.resolve("zolt.toml"), cachedState, collectedState));
        line(content, "lockfile", fileHash(lockfilePath, cachedState, collectedState));
        section(content, "sourceRoots", sourceRoots.stream().map(BuildFingerprintService::normalize).toList());
        line(content, "outputDirectory", normalize(outputDirectoryName));
        line(content, "generatedSourcesDirectory", relative(projectRoot, generatedSourcesDirectory));
        line(content, "compilerSettings", config.compilerSettings().toString());
        section(content, "compileClasspath", classpathEntries(compileClasspath, cachedState, collectedState));
        section(content, "processorClasspath", classpathEntries(processorClasspath, cachedState, collectedState));
        section(content, "sources", fileEntries(projectRoot, sources, cachedState, collectedState));
        section(content, "generatedSourceInputs", generatedSourceInputEntries(projectRoot, generatedSteps, cachedState, collectedState));
        section(content, "resources", resourceEntries(projectRoot, resourceRoots, config.build(), cachedState, collectedState));
        section(content, "generatedSources", generatedSourceEntries(projectRoot, generatedSourcesDirectory, cachedState, collectedState));
        section(content, "expectedClasses", expectedClassEntries(projectRoot, sourceRoots, sources, outputDirectory));
        return content.toString();
    }

    private static List<String> classpathEntries(
            Classpath classpath,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> path + "|" + fileHash(path, cachedState, collectedState))
                .toList();
    }

    private static List<String> fileEntries(
            Path projectRoot,
            List<Path> files,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
        return files.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> relative(projectRoot, path) + "|" + fileHash(path, cachedState, collectedState))
                .toList();
    }

    private static List<String> resourceEntries(
            Path projectRoot,
            List<String> configuredRoots,
            BuildSettings settings,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
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
        return fileEntries(projectRoot, resources, cachedState, collectedState);
    }

    private static List<String> generatedSourceInputEntries(
            Path projectRoot,
            List<GeneratedSourceStep> steps,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
        return steps.stream()
                .flatMap(step -> step.inputs().stream())
                .map(input -> projectRoot.resolve(input).normalize())
                .distinct()
                .sorted()
                .map(path -> relative(projectRoot, path) + "|" + fileHash(path, cachedState, collectedState))
                .toList();
    }

    private static List<String> generatedSourceEntries(
            Path projectRoot,
            Path generatedSourcesDirectory,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
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
        return fileHash(path, null, null);
    }

    private static String fileHash(
            Path path,
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return directoryHash(normalized, cachedState, collectedState);
        }
        if (!Files.isRegularFile(normalized)) {
            return "missing";
        }
        if (cachedState != null) {
            return cachedState.hashIfCurrent(normalized)
                    .orElseThrow(FingerprintStateMiss::new);
        }
        try {
            String hash = sha256(Files.readAllBytes(normalized));
            if (collectedState != null) {
                collectedState.put(normalized, CachedFileHash.read(normalized, hash));
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
            FingerprintState cachedState,
            Map<Path, CachedFileHash> collectedState) {
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

    private static Optional<FingerprintState> readState(Path fingerprintPath) {
        Path statePath = statePath(fingerprintPath);
        if (!Files.isRegularFile(statePath)) {
            return Optional.empty();
        }
        try {
            return FingerprintState.parse(Files.readAllLines(statePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read build fingerprint state at "
                            + statePath
                            + ". Delete the file or rerun `zolt build` to refresh it.",
                    exception);
        }
    }

    private static void writeState(Path fingerprintPath, String fingerprint, Map<Path, CachedFileHash> collectedState) {
        Path statePath = statePath(fingerprintPath);
        try {
            Files.writeString(
                    statePath,
                    FingerprintState.format(fingerprint, collectedState),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write build fingerprint state at "
                            + statePath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    private record CachedFileHash(
            Path path,
            long size,
            long lastModifiedNanos,
            String hash) {
        static CachedFileHash read(Path path, String hash) throws IOException {
            Path normalized = path.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class);
            return new CachedFileHash(
                    normalized,
                    attributes.size(),
                    attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                    hash);
        }

        boolean isCurrent() {
            try {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                return attributes.isRegularFile()
                        && attributes.size() == size
                        && attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) == lastModifiedNanos;
            } catch (IOException exception) {
                return false;
            }
        }
    }

    private record FingerprintState(
            String fingerprintSha256,
            Map<Path, CachedFileHash> files) {
        static Optional<FingerprintState> parse(List<String> lines) {
            if (lines.size() < 2 || !("version=" + STATE_VERSION).equals(lines.get(0))) {
                return Optional.empty();
            }
            String fingerprintSha256 = value(lines.get(1), "fingerprintSha256=");
            if (fingerprintSha256 == null || fingerprintSha256.isBlank()) {
                return Optional.empty();
            }
            Map<Path, CachedFileHash> files = new LinkedHashMap<>();
            for (int index = 2; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length != 5 || !"file".equals(parts[0])) {
                    return Optional.empty();
                }
                try {
                    Path path = Path.of(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                            .toAbsolutePath()
                            .normalize();
                    long size = Long.parseLong(parts[2]);
                    long lastModifiedNanos = Long.parseLong(parts[3]);
                    files.put(path, new CachedFileHash(path, size, lastModifiedNanos, parts[4]));
                } catch (IllegalArgumentException exception) {
                    return Optional.empty();
                }
            }
            return Optional.of(new FingerprintState(fingerprintSha256, Map.copyOf(files)));
        }

        static String format(String fingerprint, Map<Path, CachedFileHash> files) {
            StringBuilder content = new StringBuilder();
            line(content, "version", STATE_VERSION);
            line(content, "fingerprintSha256", sha256(fingerprint.getBytes(StandardCharsets.UTF_8)));
            files.values().stream()
                    .sorted(Comparator.comparing(cached -> cached.path().toString()))
                    .forEach(cached -> content
                            .append("file")
                            .append('\t')
                            .append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                                    cached.path().toString().getBytes(StandardCharsets.UTF_8)))
                            .append('\t')
                            .append(cached.size())
                            .append('\t')
                            .append(cached.lastModifiedNanos())
                            .append('\t')
                            .append(cached.hash())
                            .append('\n'));
            return content.toString();
        }

        boolean matchesFingerprint(String fingerprint) {
            return fingerprintSha256.equals(sha256(fingerprint.getBytes(StandardCharsets.UTF_8)));
        }

        Optional<String> hashIfCurrent(Path path) {
            CachedFileHash file = files.get(path.toAbsolutePath().normalize());
            if (file == null || !file.isCurrent()) {
                return Optional.empty();
            }
            return Optional.of(file.hash());
        }

        private static String value(String line, String prefix) {
            return line.startsWith(prefix) ? line.substring(prefix.length()) : null;
        }
    }

    private static final class FingerprintStateMiss extends RuntimeException {
    }
}
