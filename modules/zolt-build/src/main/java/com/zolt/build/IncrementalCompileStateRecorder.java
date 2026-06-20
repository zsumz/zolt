package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.classpath.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class IncrementalCompileStateRecorder {
    private static final String MAIN_FINGERPRINT_FILE = ".zolt-build-main.fingerprint";
    private static final String TEST_FINGERPRINT_FILE = ".zolt-build-test.fingerprint";
    private static final Set<String> LOCAL_COMPILE_METADATA = Set.of(
            MAIN_FINGERPRINT_FILE,
            MAIN_FINGERPRINT_FILE + ".state",
            TEST_FINGERPRINT_FILE,
            TEST_FINGERPRINT_FILE + ".state",
            IncrementalCompileState.MAIN_FILE_NAME,
            IncrementalCompileState.TEST_FILE_NAME);

    private final IncrementalCompileStateCodec codec;
    private final ClassFileAbiReader abiReader;
    private final IncrementalCompileSourceRecordBuilder sourceRecordBuilder;

    IncrementalCompileStateRecorder() {
        this(
                new IncrementalCompileStateCodec(),
                new ClassFileAbiReader(),
                new IncrementalCompileSourceRecordBuilder());
    }

    IncrementalCompileStateRecorder(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader) {
        this(codec, abiReader, new IncrementalCompileSourceRecordBuilder());
    }

    IncrementalCompileStateRecorder(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader,
            IncrementalCompileSourceRecordBuilder sourceRecordBuilder) {
        this.codec = codec;
        this.abiReader = abiReader;
        this.sourceRecordBuilder = sourceRecordBuilder;
    }

    void deleteMainState(Path outputDirectory) {
        deleteState(IncrementalCompileState.mainStatePath(outputDirectory));
    }

    void deleteTestState(Path outputDirectory) {
        deleteState(IncrementalCompileState.testStatePath(outputDirectory));
    }

    void recordMain(
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        record(
                "main",
                projectDirectory,
                config,
                sources.mainSources(),
                List.of(config.build().source()),
                config.build().generatedMainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory,
                IncrementalCompileState.mainStatePath(outputDirectory),
                outputDirectory.resolve(MAIN_FINGERPRINT_FILE),
                classpaths.processor().entries().isEmpty() ? List.of() : List.of("processor-classpath"));
    }

    void recordTest(
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        List<String> fallbackReasons = new ArrayList<>();
        if (!processorClasspath.entries().isEmpty()) {
            fallbackReasons.add("processor-classpath");
        }
        if (!sources.groovyTestSources().isEmpty()) {
            fallbackReasons.add("groovy-test-sources");
        }
        record(
                "test",
                projectDirectory,
                config,
                sources.testSources(),
                config.build().testSources(),
                config.build().generatedTestSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                IncrementalCompileState.testStatePath(outputDirectory),
                outputDirectory.resolve(TEST_FINGERPRINT_FILE),
                fallbackReasons);
    }

    private void record(
            String scope,
            Path projectDirectory,
            ProjectConfig config,
            List<Path> sources,
            List<String> configuredSourceRoots,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            Path statePath,
            Path fingerprintPath,
            List<String> fallbackReasons) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        List<String> stateFallbackReasons = new ArrayList<>(fallbackReasons);
        List<Path> sourceRoots = sourceRoots(projectRoot, configuredSourceRoots, generatedSteps);
        Map<Path, String> generatedStepIds = generatedStepIds(projectRoot, generatedSteps);
        List<ClassFileAbi> classFiles = classFiles(outputDirectory, stateFallbackReasons);
        List<IncrementalCompileState.SourceRecord> sourceRecords = sourceRecordBuilder.sourceRecords(
                projectRoot,
                sources,
                sourceRoots,
                generatedStepIds,
                classFiles,
                IncrementalCompileStateRecorder::fileHash);
        List<IncrementalCompileState.ClassRecord> classRecords = classRecords(classFiles);
        codec.write(
                statePath,
                new IncrementalCompileState(
                        scope,
                        projectRoot,
                        outputDirectory,
                        generatedSourcesDirectory,
                        sha256(config.compilerSettings().toString().getBytes(StandardCharsets.UTF_8)),
                        fileHash(fingerprintPath),
                        stateFallbackReasons,
                        sourceRoots.stream().map(path -> relative(projectRoot, path)).toList(),
                        generatedSteps.stream().map(GeneratedSourceStep::output).sorted().toList(),
                        classpathEntries(compileClasspath),
                        classpathEntries(processorClasspath),
                        sourceRecords,
                        classRecords,
                        reverseDependencies(sourceRecords)));
    }

    private List<ClassFileAbi> classFiles(Path outputDirectory, List<String> fallbackReasons) {
        if (!Files.isDirectory(outputDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .flatMap(path -> readAbi(path, fallbackReasons).stream())
                    .toList();
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not inspect compiled classes under "
                            + outputDirectory
                            + ". Check that the directory is readable.",
                    exception);
        }
    }

    private Optional<ClassFileAbi> readAbi(Path classFile, List<String> fallbackReasons) {
        try {
            return Optional.of(abiReader.read(classFile));
        } catch (BuildException exception) {
            if (!fallbackReasons.contains("unreadable-class-output")) {
                fallbackReasons.add("unreadable-class-output");
            }
            return Optional.empty();
        }
    }

    private static List<IncrementalCompileState.ClassRecord> classRecords(List<ClassFileAbi> classFiles) {
        return classFiles.stream()
                .map(abi -> new IncrementalCompileState.ClassRecord(
                        abi.binaryName(),
                        abi.classFile(),
                        fileHash(abi.classFile()),
                        abi.abiHash(),
                        abi.packagePrivateAbiHash(),
                        abi.accessFlags(),
                        abi.superName(),
                        abi.interfaces()))
                .toList();
    }

    private static Map<String, List<Path>> reverseDependencies(List<IncrementalCompileState.SourceRecord> sourceRecords) {
        Map<String, List<Path>> reverseDependencies = new LinkedHashMap<>();
        for (IncrementalCompileState.SourceRecord source : sourceRecords) {
            for (String referencedClass : source.referencedClasses()) {
                reverseDependencies
                        .computeIfAbsent(referencedClass, ignored -> new ArrayList<>())
                        .add(source.path());
            }
        }
        return reverseDependencies;
    }

    private static List<IncrementalCompileState.ClasspathEntry> classpathEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> new IncrementalCompileState.ClasspathEntry(path, fileHash(path)))
                .toList();
    }

    private static List<Path> sourceRoots(
            Path projectRoot,
            List<String> configuredSourceRoots,
            List<GeneratedSourceStep> generatedSteps) {
        List<Path> roots = new ArrayList<>();
        configuredSourceRoots.stream()
                .map(root -> projectRoot.resolve(root).normalize())
                .forEach(roots::add);
        generatedSteps.stream()
                .map(step -> projectRoot.resolve(step.output()).normalize())
                .forEach(roots::add);
        return roots.stream().distinct().sorted().toList();
    }

    private static Map<Path, String> generatedStepIds(Path projectRoot, List<GeneratedSourceStep> generatedSteps) {
        Map<Path, String> ids = new LinkedHashMap<>();
        generatedSteps.stream()
                .sorted(Comparator.comparing(GeneratedSourceStep::id))
                .forEach(step -> ids.put(projectRoot.resolve(step.output()).normalize(), step.id()));
        return ids;
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
                    "Could not hash incremental compile input "
                            + normalized
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String directoryHash(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            StringBuilder content = new StringBuilder();
            paths.filter(Files::isRegularFile)
                    .filter(path -> !LOCAL_COMPILE_METADATA.contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> content
                            .append(directory.relativize(path).toString().replace('\\', '/'))
                            .append('|')
                            .append(fileHash(path))
                            .append('\n'));
            return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not hash incremental compile directory "
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
            throw new BuildException("Could not compute incremental compile state because SHA-256 is unavailable.", exception);
        }
    }

    private static void deleteState(Path statePath) {
        try {
            Files.deleteIfExists(statePath);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not delete stale incremental compile state at "
                            + statePath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }
}
