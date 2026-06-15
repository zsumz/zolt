package com.zolt.build;

import static com.zolt.build.IncrementalCompileInputHasher.hash;
import static com.zolt.build.IncrementalCompileInputHasher.hashText;
import static com.zolt.build.IncrementalCompileInputHasher.relative;

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
        if (!state.compilerSettingsHash().equals(hashText(config.compilerSettings().toString()))) {
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

    IncrementalValidation validateAfterIncrementalCompile(Plan plan) {
        if (!plan.incremental()) {
            return IncrementalValidation.success(List.of());
        }
        Set<Path> scheduledSources = new LinkedHashSet<>(plan.sourcesToCompile());
        List<Path> additionalSources = new ArrayList<>();
        int abiChangedClasses = 0;
        int packagePrivateAbiChangedClasses = 0;
        for (IncrementalCompileState.SourceRecord previousSource : plan.changedPreviousRecords()) {
            Path output = previousSource.classOutputs().getFirst();
            ClassFileAbi currentAbi;
            try {
                currentAbi = abiReader.read(output);
            } catch (BuildException exception) {
                return IncrementalValidation.fallback("changed-class-output-missing");
            }
            Optional<IncrementalCompileState.ClassRecord> previousClass = plan.previousClass(output);
            if (previousClass.isEmpty()) {
                return IncrementalValidation.fallback("changed-class-state-missing");
            }
            IncrementalCompileState.ClassRecord classRecord = previousClass.orElseThrow();
            boolean abiChanged = !classRecord.abiHash().equals(currentAbi.abiHash());
            boolean packagePrivateAbiChanged = !classRecord.packagePrivateAbiHash().equals(currentAbi.packagePrivateAbiHash())
                    && !abiChanged;
            if (abiChanged) {
                abiChangedClasses++;
                addAffectedSources(
                        additionalSources,
                        scheduledSources,
                        plan.reverseDependencies().getOrDefault(classRecord.binaryName(), List.of()),
                        plan);
            }
            if (packagePrivateAbiChanged) {
                packagePrivateAbiChangedClasses++;
                addSamePackageSources(
                        additionalSources,
                        scheduledSources,
                        previousSource.packageName(),
                        plan);
            }
        }
        return IncrementalValidation.success(
                additionalSources,
                abiChangedClasses,
                packagePrivateAbiChangedClasses);
    }

    private static void addAffectedSources(
            List<Path> additionalSources,
            Set<Path> scheduledSources,
            List<Path> candidates,
            Plan plan) {
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (plan.hasSource(normalized) && scheduledSources.add(normalized)) {
                additionalSources.add(normalized);
            }
        }
    }

    private static void addSamePackageSources(
            List<Path> additionalSources,
            Set<Path> scheduledSources,
            String packageName,
            Plan plan) {
        for (IncrementalCompileState.SourceRecord source : plan.previousSources().values()) {
            if (source.packageName().equals(packageName) && scheduledSources.add(source.path())) {
                additionalSources.add(source.path());
            }
        }
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
                .map(path -> new IncrementalCompileState.ClasspathEntry(path, hash(path)))
                .toList();
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
