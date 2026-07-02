package com.zolt.build.incremental;

import static com.zolt.build.incremental.IncrementalCompileInputHasher.hash;

import com.zolt.build.abi.ClassFileAbiReader;
import com.zolt.build.discovery.SourceDiscoveryResult;
import com.zolt.classpath.Classpath;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class IncrementalCompilePlanner {
    private final IncrementalCompileStateCodec codec;
    private final IncrementalCompileStateValidator stateValidator;
    private final IncrementalCompileAbiValidator abiValidator;

    public IncrementalCompilePlanner() {
        this(new IncrementalCompileStateCodec(), new ClassFileAbiReader());
    }

    IncrementalCompilePlanner(
            IncrementalCompileStateCodec codec,
            ClassFileAbiReader abiReader) {
        this.codec = codec;
        this.stateValidator = new IncrementalCompileStateValidator();
        this.abiValidator = new IncrementalCompileAbiValidator(abiReader);
    }

    public IncrementalCompilePlan planMain(
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
                config.build().sourceRoots(),
                config.build().generatedMainSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                IncrementalCompileState.mainStatePath(outputDirectory),
                List.of());
    }

    public IncrementalCompilePlan planTest(
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

    private IncrementalCompilePlan plan(
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
            return IncrementalCompilePlan.full("processor-classpath");
        }
        if (!additionalFallbackReasons.isEmpty()) {
            return IncrementalCompilePlan.full(additionalFallbackReasons.getFirst());
        }
        Optional<IncrementalCompileState> optionalState = codec.read(statePath);
        if (optionalState.isEmpty()) {
            return IncrementalCompilePlan.full("missing-state");
        }
        IncrementalCompileState state = optionalState.orElseThrow();
        if (!state.fallbackReasons().isEmpty()) {
            return IncrementalCompilePlan.full(state.fallbackReasons().getFirst());
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
            return IncrementalCompilePlan.full(validationFailure);
        }

        Map<Path, IncrementalCompileState.SourceRecord> previousSources = new LinkedHashMap<>();
        for (IncrementalCompileState.SourceRecord source : state.sources()) {
            previousSources.put(source.path(), source);
        }
        Map<Path, String> currentHashes = currentSourceHashes(sources);
        Set<Path> deletedSources = new LinkedHashSet<>(previousSources.keySet());
        deletedSources.removeAll(currentHashes.keySet());
        if (!deletedSources.isEmpty()) {
            return IncrementalCompilePlan.full(
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
                    return IncrementalCompilePlan.full("multi-class-source");
                }
                dirtySources.add(entry.getKey());
                changedPreviousRecords.add(previous);
            }
        }
        if (dirtySources.isEmpty()) {
            return IncrementalCompilePlan.full("non-source-input-changed");
        }
        return IncrementalCompilePlan.incremental(
                dirtySources,
                addedSourceCount,
                changedPreviousRecords,
                state.sources(),
                state.classes(),
                state.reverseDependencies());
    }

    public IncrementalCompileValidation validateAfterIncrementalCompile(IncrementalCompilePlan plan) {
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

}
