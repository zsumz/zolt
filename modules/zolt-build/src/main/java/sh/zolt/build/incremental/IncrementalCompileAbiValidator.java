package sh.zolt.build.incremental;

import sh.zolt.build.BuildException;
import sh.zolt.build.abi.ClassFileAbi;
import sh.zolt.build.abi.ClassFileAbiReader;
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

    IncrementalCompileValidation validate(IncrementalCompilePlan plan) {
        if (!plan.incremental()) {
            return IncrementalCompileValidation.success(List.of());
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
                return IncrementalCompileValidation.fallback("changed-class-output-missing");
            }
            Optional<IncrementalCompileState.ClassRecord> previousClass = plan.previousClass(output);
            if (previousClass.isEmpty()) {
                return IncrementalCompileValidation.fallback("changed-class-state-missing");
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
        return IncrementalCompileValidation.success(
                additionalSources,
                abiChangedClasses,
                packagePrivateAbiChangedClasses);
    }

    private static void addAffectedSources(
            List<Path> additionalSources,
            Set<Path> scheduledSources,
            List<Path> candidates,
            IncrementalCompilePlan plan) {
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
            IncrementalCompilePlan plan) {
        for (IncrementalCompileState.SourceRecord source : plan.previousSources().values()) {
            if (source.packageName().equals(packageName) && scheduledSources.add(source.path())) {
                additionalSources.add(source.path());
            }
        }
    }
}
