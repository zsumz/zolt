package com.zolt.build;

import static com.zolt.build.IncrementalCompileInputHasher.hash;

import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.classpath.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class IncrementalCompilePlanner {
    private final IncrementalCompileStateCodec codec;
    private final IncrementalCompileStateValidator stateValidator;
    private final IncrementalCompileAbiValidator abiValidator;

    IncrementalCompilePlanner() {
        this(new IncrementalCompileStateCodec(), new ClassFileAbiReader());
    }

    IncrementalCompilePlanner(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader) {
        this.codec = codec;
        this.stateValidator = new IncrementalCompileStateValidator();
        this.abiValidator = new IncrementalCompileAbiValidator(abiReader);
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
        String validationFailure = stateValidator.validate(
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
            return Plan.full(
                    "source-deleted",
                    outputsForDeletedSources(deletedSources, previousSources),
                    deletedSources.size());
        }

        List<Path> dirtySources = new ArrayList<>();
        List<IncrementalCompileState.SourceRecord> changedPreviousRecords = new ArrayList<>();
        int addedSourceCount = 0;
        for (Map.Entry<Path, String> entry : currentHashes.entrySet()) {
            IncrementalCompileState.SourceRecord previous = previousSources.get(entry.getKey());
            if (previous == null) {
                dirtySources.add(entry.getKey());
                addedSourceCount++;
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
        return Plan.incremental(
                dirtySources,
                addedSourceCount,
                changedPreviousRecords,
                state.sources(),
                state.classes(),
                state.reverseDependencies());
    }

    IncrementalValidation validateAfterIncrementalCompile(Plan plan) {
        return abiValidator.validate(plan);
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
                .forEach(path -> hashes.put(path, hash(path)));
        return hashes;
    }

    record Plan(
            boolean incremental,
            List<Path> sourcesToCompile,
            String fallbackReason,
            List<Path> outputsToDelete,
            int sourcesAdded,
            int sourcesChanged,
            int sourcesDeleted,
            List<IncrementalCompileState.SourceRecord> changedPreviousRecords,
            Map<Path, IncrementalCompileState.SourceRecord> previousSources,
            Map<Path, IncrementalCompileState.ClassRecord> previousClasses,
            Map<String, List<Path>> reverseDependencies) {
        static Plan full(String reason) {
            return full(reason, List.of());
        }

        static Plan full(String reason, List<Path> outputsToDelete) {
            return full(reason, outputsToDelete, 0);
        }

        static Plan full(String reason, List<Path> outputsToDelete, int sourcesDeleted) {
            return new Plan(
                    false,
                    List.of(),
                    reason,
                    outputsToDelete.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList(),
                    0,
                    0,
                    sourcesDeleted,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    Map.of());
        }

        static Plan incremental(
                List<Path> sourcesToCompile,
                int sourcesAdded,
                List<IncrementalCompileState.SourceRecord> changedPreviousRecords,
                List<IncrementalCompileState.SourceRecord> sourceRecords,
                List<IncrementalCompileState.ClassRecord> classRecords,
                Map<String, List<Path>> reverseDependencies) {
            Map<Path, IncrementalCompileState.SourceRecord> previousSources = new LinkedHashMap<>();
            for (IncrementalCompileState.SourceRecord sourceRecord : sourceRecords) {
                previousSources.put(sourceRecord.path(), sourceRecord);
            }
            Map<Path, IncrementalCompileState.ClassRecord> previousClasses = new LinkedHashMap<>();
            for (IncrementalCompileState.ClassRecord classRecord : classRecords) {
                previousClasses.put(classRecord.outputPath(), classRecord);
            }
            return new Plan(
                    true,
                    sourcesToCompile.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList(),
                    "",
                    List.of(),
                    sourcesAdded,
                    changedPreviousRecords.size(),
                    0,
                    List.copyOf(changedPreviousRecords),
                    previousSources,
                    previousClasses,
                    reverseDependencies == null ? Map.of() : reverseDependencies);
        }

        Optional<IncrementalCompileState.ClassRecord> previousClass(Path output) {
            Path normalized = output.toAbsolutePath().normalize();
            return Optional.ofNullable(previousClasses.get(normalized));
        }

        boolean hasSource(Path source) {
            return previousSources.containsKey(source.toAbsolutePath().normalize());
        }

        CompileDiagnostics diagnostics(int sourcesRecompiled, IncrementalValidation validation) {
            return new CompileDiagnostics(
                    sourcesAdded,
                    sourcesChanged,
                    sourcesDeleted,
                    sourcesRecompiled,
                    validation.additionalSources().size(),
                    outputsToDelete.size(),
                    validation.abiChangedClasses(),
                    validation.packagePrivateAbiChangedClasses());
        }

        CompileDiagnostics fullDiagnostics(int sourcesRecompiled) {
            return new CompileDiagnostics(
                    sourcesAdded,
                    sourcesChanged,
                    sourcesDeleted,
                    sourcesRecompiled,
                    0,
                    outputsToDelete.size(),
                    0,
                    0);
        }
    }

    record IncrementalValidation(
            String fallbackReason,
            List<Path> additionalSources,
            int abiChangedClasses,
            int packagePrivateAbiChangedClasses) {
        IncrementalValidation {
            fallbackReason = fallbackReason == null ? "" : fallbackReason;
            additionalSources = additionalSources == null
                    ? List.of()
                    : additionalSources.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList();
            abiChangedClasses = Math.max(0, abiChangedClasses);
            packagePrivateAbiChangedClasses = Math.max(0, packagePrivateAbiChangedClasses);
        }

        static IncrementalValidation success(List<Path> additionalSources) {
            return success(additionalSources, 0, 0);
        }

        static IncrementalValidation success(
                List<Path> additionalSources,
                int abiChangedClasses,
                int packagePrivateAbiChangedClasses) {
            return new IncrementalValidation(
                    "",
                    additionalSources,
                    abiChangedClasses,
                    packagePrivateAbiChangedClasses);
        }

        static IncrementalValidation fallback(String reason) {
            return new IncrementalValidation(reason, List.of(), 0, 0);
        }

        boolean hasFallback() {
            return !fallbackReason.isBlank();
        }
    }
}
