package com.zolt.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class IncrementalCompileAbiValidator {
    private final ClassFileAbiReader abiReader;

    IncrementalCompileAbiValidator(ClassFileAbiReader abiReader) {
        this.abiReader = abiReader;
    }

    IncrementalCompilePlanner.IncrementalValidation validate(IncrementalCompilePlanner.Plan plan) {
        if (!plan.incremental()) {
            return IncrementalCompilePlanner.IncrementalValidation.success(List.of());
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
                return IncrementalCompilePlanner.IncrementalValidation.fallback("changed-class-output-missing");
            }
            Optional<IncrementalCompileState.ClassRecord> previousClass = plan.previousClass(output);
            if (previousClass.isEmpty()) {
                return IncrementalCompilePlanner.IncrementalValidation.fallback("changed-class-state-missing");
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
        return IncrementalCompilePlanner.IncrementalValidation.success(
                additionalSources,
                abiChangedClasses,
                packagePrivateAbiChangedClasses);
    }

    private static void addAffectedSources(
            List<Path> additionalSources,
            Set<Path> scheduledSources,
            List<Path> candidates,
            IncrementalCompilePlanner.Plan plan) {
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
            IncrementalCompilePlanner.Plan plan) {
        for (IncrementalCompileState.SourceRecord source : plan.previousSources().values()) {
            if (source.packageName().equals(packageName) && scheduledSources.add(source.path())) {
                additionalSources.add(source.path());
            }
        }
    }
}
