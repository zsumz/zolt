package com.zolt.build;

import com.zolt.project.GeneratedSourceStep;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class IncrementalCompilePlanner {
    private static final Set<String> LOCAL_COMPILE_METADATA = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            IncrementalCompileState.MAIN_FILE_NAME,
            IncrementalCompileState.TEST_FILE_NAME);

    private final IncrementalCompileStateCodec codec;
    private final ClassFileAbiReader abiReader;

    IncrementalCompilePlanner() {
        this(new IncrementalCompileStateCodec(), new ClassFileAbiReader());
    }

    IncrementalCompilePlanner(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader) {
        this.codec = codec;
        this.abiReader = abiReader;
    }

    Plan planMain(
            Path projectDirectory,
            ProjectConfig config,
            List<Path> sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        return plan(
                "main",
                projectDirectory,
                config,
                sources,
                List.of(config.build().source()),
                config.build().generatedMainSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                IncrementalCompileState.mainStatePath(outputDirectory),
                List.of());
    }

    Plan planTest(
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        List<String> fallbackReasons = new ArrayList<>();
        if (!sources.groovyTestSources().isEmpty()) {
            fallbackReasons.add("groovy-test-sources");
        }
        return plan(
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
                fallbackReasons);
    }

    private Plan plan(
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
            List<String> additionalFallbackReasons) {
        if (!processorClasspath.entries().isEmpty()) {
            return Plan.full("processor-classpath");
        }
        if (!additionalFallbackReasons.isEmpty()) {
            return Plan.full(additionalFallbackReasons.getFirst());
        }
        Optional<IncrementalCompileState> optionalState = codec.read(statePath);
        if (optionalState.isEmpty()) {
            return Plan.full("missing-state");
        }
        IncrementalCompileState state = optionalState.orElseThrow();
        if (!state.fallbackReasons().isEmpty()) {
            return Plan.full(state.fallbackReasons().getFirst());
        }
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        String validationFailure = validateState(
                state,
                scope,
                projectRoot,
                config,
                configuredSourceRoots,
                generatedSteps,
                compileClasspath,
                outputDirectory,
                generatedSourcesDirectory);
        if (!validationFailure.isBlank()) {
            return Plan.full(validationFailure);
        }

        Map<Path, IncrementalCompileState.SourceRecord> previousSources = new LinkedHashMap<>();
        for (IncrementalCompileState.SourceRecord source : state.sources()) {
            previousSources.put(source.path(), source);
        }
        Map<Path, String> currentHashes = currentSourceHashes(sources);
        Set<Path> deletedSources = new LinkedHashSet<>(previousSources.keySet());
        deletedSources.removeAll(currentHashes.keySet());
        if (!deletedSources.isEmpty()) {
            return Plan.full("source-deleted", outputsForDeletedSources(deletedSources, previousSources));
        }

        List<Path> dirtySources = new ArrayList<>();
        List<IncrementalCompileState.SourceRecord> changedPreviousRecords = new ArrayList<>();
        for (Map.Entry<Path, String> entry : currentHashes.entrySet()) {
            IncrementalCompileState.SourceRecord previous = previousSources.get(entry.getKey());
            if (previous == null) {
                dirtySources.add(entry.getKey());
                continue;
            }
            if (!previous.contentHash().equals(entry.getValue())) {
                if (previous.classOutputs().size() != 1) {
                    return Plan.full("multi-class-source");
                }
                dirtySources.add(entry.getKey());
                changedPreviousRecords.add(previous);
            }
        }
        if (dirtySources.isEmpty()) {
            return Plan.full("non-source-input-changed");
        }
        return Plan.incremental(dirtySources, changedPreviousRecords, state.classes());
    }

    private String validateState(
            IncrementalCompileState state,
            String scope,
            Path projectRoot,
            ProjectConfig config,
            List<String> configuredSourceRoots,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        if (!state.scope().equals(scope)) {
            return "state-scope-mismatch";
        }
        if (!state.projectDirectory().equals(projectRoot)
                || !state.outputDirectory().equals(outputDirectory.toAbsolutePath().normalize())
                || !state.generatedSourcesDirectory().equals(generatedSourcesDirectory.toAbsolutePath().normalize())) {
            return "state-path-mismatch";
        }
        if (!state.compilerSettingsHash().equals(sha256(config.compilerSettings().toString().getBytes(StandardCharsets.UTF_8)))) {
            return "compiler-settings-changed";
        }
        if (!state.sourceRoots().equals(sourceRoots(projectRoot, configuredSourceRoots, generatedSteps))) {
            return "source-roots-changed";
        }
        if (!state.generatedSourceRoots().equals(generatedSteps.stream().map(GeneratedSourceStep::output).sorted().toList())) {
            return "generated-source-roots-changed";
        }
        if (!state.compileClasspath().equals(classpathEntries(compileClasspath))) {
            return "compile-classpath-changed";
        }
        return "";
    }

    Optional<String> validateAfterIncrementalCompile(Plan plan) {
        if (!plan.incremental()) {
            return Optional.empty();
        }
        for (IncrementalCompileState.SourceRecord previousSource : plan.changedPreviousRecords()) {
            Path output = previousSource.classOutputs().getFirst();
            ClassFileAbi currentAbi;
            try {
                currentAbi = abiReader.read(output);
            } catch (BuildException exception) {
                return Optional.of("changed-class-output-missing");
            }
            Optional<IncrementalCompileState.ClassRecord> previousClass = plan.previousClass(output);
            if (previousClass.isEmpty()) {
                return Optional.of("changed-class-state-missing");
            }
            IncrementalCompileState.ClassRecord classRecord = previousClass.orElseThrow();
            if (!classRecord.abiHash().equals(currentAbi.abiHash())
                    || !classRecord.packagePrivateAbiHash().equals(currentAbi.packagePrivateAbiHash())) {
                return Optional.of("abi-changed");
            }
        }
        return Optional.empty();
    }

    private static List<Path> outputsForDeletedSources(
            Set<Path> deletedSources,
            Map<Path, IncrementalCompileState.SourceRecord> previousSources) {
        return deletedSources.stream()
                .flatMap(source -> previousSources.get(source).classOutputs().stream())
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted()
                .toList();
    }

    private static Map<Path, String> currentSourceHashes(List<Path> sources) {
        Map<Path, String> hashes = new LinkedHashMap<>();
        sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .forEach(path -> hashes.put(path, fileHash(path)));
        return hashes;
    }

    private static List<String> sourceRoots(
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
        return roots.stream()
                .distinct()
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    private static List<IncrementalCompileState.ClasspathEntry> classpathEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> new IncrementalCompileState.ClasspathEntry(path, fileHash(path)))
                .toList();
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
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
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
            throw new BuildException("Could not compute incremental compile plan because SHA-256 is unavailable.", exception);
        }
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    record Plan(
            boolean incremental,
            List<Path> sourcesToCompile,
            String fallbackReason,
            List<Path> outputsToDelete,
            List<IncrementalCompileState.SourceRecord> changedPreviousRecords,
            Map<Path, IncrementalCompileState.ClassRecord> previousClasses) {
        static Plan full(String reason) {
            return full(reason, List.of());
        }

        static Plan full(String reason, List<Path> outputsToDelete) {
            return new Plan(
                    false,
                    List.of(),
                    reason,
                    outputsToDelete.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList(),
                    List.of(),
                    Map.of());
        }

        static Plan incremental(
                List<Path> sourcesToCompile,
                List<IncrementalCompileState.SourceRecord> changedPreviousRecords,
                List<IncrementalCompileState.ClassRecord> classRecords) {
            Map<Path, IncrementalCompileState.ClassRecord> previousClasses = new LinkedHashMap<>();
            for (IncrementalCompileState.ClassRecord classRecord : classRecords) {
                previousClasses.put(classRecord.outputPath(), classRecord);
            }
            return new Plan(
                    true,
                    sourcesToCompile.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList(),
                    "",
                    List.of(),
                    List.copyOf(changedPreviousRecords),
                    previousClasses);
        }

        Optional<IncrementalCompileState.ClassRecord> previousClass(Path output) {
            Path normalized = output.toAbsolutePath().normalize();
            return Optional.ofNullable(previousClasses.get(normalized));
        }
    }
}
