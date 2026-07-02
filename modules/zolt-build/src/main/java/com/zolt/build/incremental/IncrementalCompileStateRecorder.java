package com.zolt.build.incremental;

import static com.zolt.build.incremental.IncrementalCompileInputHasher.hash;
import static com.zolt.build.incremental.IncrementalCompileInputHasher.hashText;
import static com.zolt.build.incremental.IncrementalCompileInputHasher.relative;

import com.zolt.build.BuildException;
import com.zolt.build.abi.ClassFileAbi;
import com.zolt.build.abi.ClassFileAbiReader;
import com.zolt.build.discovery.SourceDiscoveryResult;
import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class IncrementalCompileStateRecorder {
    private static final String MAIN_FINGERPRINT_FILE = ".zolt-build-main.fingerprint";
    private static final String TEST_FINGERPRINT_FILE = ".zolt-build-test.fingerprint";

    private final IncrementalCompileStateCodec codec;
    private final ClassFileAbiReader abiReader;
    private final IncrementalCompileSourceRecordBuilder sourceRecordBuilder;

    public IncrementalCompileStateRecorder() {
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

    public void deleteMainState(Path outputDirectory) {
        deleteState(IncrementalCompileState.mainStatePath(outputDirectory));
    }

    public void deleteTestState(Path outputDirectory) {
        deleteState(IncrementalCompileState.testStatePath(outputDirectory));
    }

    public void recordMain(
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
                config.build().sourceRoots(),
                config.build().generatedMainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory,
                IncrementalCompileState.mainStatePath(outputDirectory),
                outputDirectory.resolve(MAIN_FINGERPRINT_FILE),
                classpaths.processor().entries().isEmpty() ? List.of() : List.of("processor-classpath"));
    }

    public void recordTest(
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
                IncrementalCompileInputHasher::hash);
        List<IncrementalCompileState.ClassRecord> classRecords = classRecords(classFiles);
        codec.write(
                statePath,
                new IncrementalCompileState(
                        scope,
                        projectRoot,
                        outputDirectory,
                        generatedSourcesDirectory,
                        hashText(config.compilerSettings().toString()),
                        hash(fingerprintPath),
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
                        hash(abi.classFile()),
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
                .map(path -> new IncrementalCompileState.ClasspathEntry(path, hash(path)))
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
}
