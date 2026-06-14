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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;");
    private static final Pattern TOP_LEVEL_TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w.$]+(?:\\([^\\n]*\\))?\\s*)*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|static\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private final IncrementalCompileStateCodec codec;
    private final ClassFileAbiReader abiReader;

    IncrementalCompileStateRecorder() {
        this(new IncrementalCompileStateCodec(), new ClassFileAbiReader());
    }

    IncrementalCompileStateRecorder(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader) {
        this.codec = codec;
        this.abiReader = abiReader;
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
        Map<SourceKey, List<ClassFileAbi>> classesBySource = classesBySource(classFiles);
        List<IncrementalCompileState.SourceRecord> sourceRecords = sourceRecords(
                projectRoot,
                sources,
                sourceRoots,
                generatedStepIds,
                classesBySource);
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

    private List<IncrementalCompileState.SourceRecord> sourceRecords(
            Path projectRoot,
            List<Path> sources,
            List<Path> sourceRoots,
            Map<Path, String> generatedStepIds,
            Map<SourceKey, List<ClassFileAbi>> classesBySource) {
        List<IncrementalCompileState.SourceRecord> records = new ArrayList<>();
        for (Path source : sources.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList()) {
            SourceMetadata metadata = sourceMetadata(source);
            Path sourceRoot = sourceRootFor(sourceRoots, source).orElse(projectRoot);
            List<ClassFileAbi> ownedClasses = classesBySource.getOrDefault(
                    new SourceKey(metadata.packageName(), source.getFileName().toString()),
                    List.of());
            List<String> references = ownedClasses.stream()
                    .flatMap(abi -> abi.referencedClasses().stream())
                    .distinct()
                    .sorted()
                    .toList();
            records.add(new IncrementalCompileState.SourceRecord(
                    source,
                    sourceRoot,
                    generatedSourceStepId(generatedStepIds, source),
                    fileHash(source),
                    metadata.packageName(),
                    metadata.declaredTypes(),
                    ownedClasses.stream().map(ClassFileAbi::classFile).toList(),
                    references));
        }
        return records;
    }

    private static Optional<String> generatedSourceStepId(Map<Path, String> generatedStepIds, Path source) {
        return generatedStepIds.entrySet().stream()
                .filter(entry -> source.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().getNameCount()))
                .map(Map.Entry::getValue);
    }

    private static Optional<Path> sourceRootFor(List<Path> sourceRoots, Path source) {
        return sourceRoots.stream()
                .filter(source::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount));
    }

    private SourceMetadata sourceMetadata(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            String packageName = "";
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }
            Matcher typeMatcher = TOP_LEVEL_TYPE_PATTERN.matcher(content);
            Set<String> declaredTypes = new LinkedHashSet<>();
            while (typeMatcher.find()) {
                declaredTypes.add(packageName.isBlank()
                        ? typeMatcher.group(1)
                        : packageName + "." + typeMatcher.group(1));
            }
            return new SourceMetadata(packageName, declaredTypes.stream().sorted().toList());
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read source metadata from "
                            + source
                            + ". Check that the source file is readable.",
                    exception);
        }
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

    private static Map<SourceKey, List<ClassFileAbi>> classesBySource(List<ClassFileAbi> classes) {
        Map<SourceKey, List<ClassFileAbi>> mapped = new LinkedHashMap<>();
        for (ClassFileAbi abi : classes) {
            abi.sourceFileName().ifPresent(sourceFileName -> mapped
                    .computeIfAbsent(new SourceKey(packageName(abi.binaryName()), sourceFileName), ignored -> new ArrayList<>())
                    .add(abi));
        }
        mapped.values().forEach(values -> values.sort(Comparator.comparing(ClassFileAbi::binaryName)));
        return mapped;
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

    private static String packageName(String binaryName) {
        int index = binaryName.lastIndexOf('.');
        return index < 0 ? "" : binaryName.substring(0, index);
    }

    private record SourceKey(String packageName, String sourceFileName) {
    }

    private record SourceMetadata(String packageName, List<String> declaredTypes) {
    }
}
