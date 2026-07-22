package sh.zolt.build.incremental;

import sh.zolt.build.BuildException;
import sh.zolt.build.abi.ClassFileAbi;
import sh.zolt.build.abi.ClassFileAbiReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class IncrementalCompileAbiValidator {
    private static final int MAX_WAVES = 10;

    private final ClassFileAbiReader abiReader;

    IncrementalCompileAbiValidator(ClassFileAbiReader abiReader) {
        this.abiReader = abiReader;
    }

    IncrementalCompileWaveResult validateWaves(
            IncrementalCompilePlan plan,
            IncrementalDependentCompiler compiler) {
        if (!plan.incremental()) {
            return new IncrementalCompileWaveResult(IncrementalCompileValidation.success(List.of()), 0, "");
        }
        Set<Path> scheduled = new LinkedHashSet<>(plan.sourcesToCompile());
        List<Path> additionalSources = new ArrayList<>();
        int abiChangedClasses = 0;
        int packagePrivateAbiChangedClasses = 0;
        int dependentSourceCount = 0;
        String dependentOutput = "";
        List<IncrementalCompileState.SourceRecord> wave = plan.changedPreviousRecords();
        int waveNumber = 0;
        while (!wave.isEmpty()) {
            if (++waveNumber > MAX_WAVES) {
                return fallback("abi-revalidation-cap");
            }
            WaveScan scan = scanWave(plan, wave, scheduled);
            if (scan.hasFallback()) {
                return fallback(scan.fallbackReason());
            }
            abiChangedClasses += scan.abiChangedClasses();
            packagePrivateAbiChangedClasses += scan.packagePrivateAbiChangedClasses();
            if (scan.newlyScheduled().isEmpty()) {
                break;
            }
            List<Path> newSources = scan.newlyScheduled().stream()
                    .map(IncrementalCompileState.SourceRecord::path)
                    .toList();
            additionalSources.addAll(newSources);
            IncrementalDependentCompiler.Outcome outcome = compiler.compile(newSources);
            dependentSourceCount += outcome.sourceCount();
            dependentOutput = combine(dependentOutput, outcome.output());
            wave = scan.newlyScheduled();
        }
        return new IncrementalCompileWaveResult(
                IncrementalCompileValidation.success(
                        additionalSources,
                        abiChangedClasses,
                        packagePrivateAbiChangedClasses),
                dependentSourceCount,
                dependentOutput);
    }

    private WaveScan scanWave(
            IncrementalCompilePlan plan,
            List<IncrementalCompileState.SourceRecord> wave,
            Set<Path> scheduled) {
        List<IncrementalCompileState.SourceRecord> newlyScheduled = new ArrayList<>();
        int abiChangedClasses = 0;
        int packagePrivateAbiChangedClasses = 0;
        for (IncrementalCompileState.SourceRecord source : wave) {
            for (Path output : source.classOutputs()) {
                Optional<IncrementalCompileState.ClassRecord> previousClass = plan.previousClass(output);
                if (previousClass.isEmpty()) {
                    return WaveScan.fallback("changed-class-state-missing");
                }
                IncrementalCompileState.ClassRecord classRecord = previousClass.orElseThrow();
                if (!Files.exists(output)) {
                    abiChangedClasses++;
                    addReverseDependents(plan, classRecord.binaryName(), scheduled, newlyScheduled);
                    continue;
                }
                ClassFileAbi currentAbi;
                try {
                    currentAbi = abiReader.read(output);
                } catch (BuildException exception) {
                    return WaveScan.fallback("changed-class-output-unreadable");
                }
                boolean abiChanged = !classRecord.abiHash().equals(currentAbi.abiHash());
                boolean packagePrivateAbiChanged =
                        !classRecord.packagePrivateAbiHash().equals(currentAbi.packagePrivateAbiHash())
                                && !abiChanged;
                if (abiChanged) {
                    abiChangedClasses++;
                    addReverseDependents(plan, classRecord.binaryName(), scheduled, newlyScheduled);
                }
                if (packagePrivateAbiChanged) {
                    packagePrivateAbiChangedClasses++;
                    addSamePackageSources(plan, source.packageName(), scheduled, newlyScheduled);
                }
            }
        }
        return WaveScan.success(newlyScheduled, abiChangedClasses, packagePrivateAbiChangedClasses);
    }

    private static void addReverseDependents(
            IncrementalCompilePlan plan,
            String binaryName,
            Set<Path> scheduled,
            List<IncrementalCompileState.SourceRecord> newlyScheduled) {
        for (Path candidate : plan.reverseDependencies().getOrDefault(binaryName, List.of())) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (plan.hasSource(normalized) && scheduled.add(normalized)) {
                newlyScheduled.add(plan.previousSources().get(normalized));
            }
        }
    }

    private static void addSamePackageSources(
            IncrementalCompilePlan plan,
            String packageName,
            Set<Path> scheduled,
            List<IncrementalCompileState.SourceRecord> newlyScheduled) {
        for (IncrementalCompileState.SourceRecord source : plan.previousSources().values()) {
            if (source.packageName().equals(packageName) && scheduled.add(source.path())) {
                newlyScheduled.add(source);
            }
        }
    }

    private static IncrementalCompileWaveResult fallback(String reason) {
        return new IncrementalCompileWaveResult(IncrementalCompileValidation.fallback(reason), 0, "");
    }

    private static String combine(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        return first.endsWith("\n") ? first + second : first + "\n" + second;
    }

    private record WaveScan(
            List<IncrementalCompileState.SourceRecord> newlyScheduled,
            int abiChangedClasses,
            int packagePrivateAbiChangedClasses,
            String fallbackReason) {
        static WaveScan success(
                List<IncrementalCompileState.SourceRecord> newlyScheduled,
                int abiChangedClasses,
                int packagePrivateAbiChangedClasses) {
            return new WaveScan(newlyScheduled, abiChangedClasses, packagePrivateAbiChangedClasses, "");
        }

        static WaveScan fallback(String reason) {
            return new WaveScan(List.of(), 0, 0, reason);
        }

        boolean hasFallback() {
            return !fallbackReason.isEmpty();
        }
    }
}
