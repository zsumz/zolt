package com.zolt.generated;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class GeneratedSourceEvidenceService {
    public List<GeneratedSourceEvidence> evidence(Path projectRoot, BuildSettings build) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<GeneratedSourceEvidence> evidence = new ArrayList<>();
        for (GeneratedSourceStep step : build.generatedMainSources()) {
            evidence.add(evidence(root, "main", step));
        }
        for (GeneratedSourceStep step : build.generatedTestSources()) {
            evidence.add(evidence(root, "test", step));
        }
        return List.copyOf(evidence);
    }

    private static GeneratedSourceEvidence evidence(Path root, String scope, GeneratedSourceStep step) {
        Path output = root.resolve(step.output()).normalize();
        List<Path> inputs = step.inputs().stream()
                .map(input -> root.resolve(input).normalize())
                .toList();
        boolean outputExists = Files.isDirectory(output);
        boolean inputsPresent = inputs.stream().allMatch(Files::exists);
        return new GeneratedSourceEvidence(
                "generated-" + scope + "-" + step.id(),
                "generated-" + scope + "-" + step.id(),
                scope,
                step,
                output,
                inputs,
                outputExists,
                inputsPresent,
                freshness(output, outputExists, inputs, inputsPresent),
                step.clean() ? "zolt-owned-clean" : "external-declared-root",
                scope.equals("main") ? "main-compile" : "test-compile");
    }

    private static String freshness(Path output, boolean outputExists, List<Path> inputs, boolean inputsPresent) {
        if (!inputsPresent) {
            return "input-missing";
        }
        if (!outputExists) {
            return "missing";
        }
        if (inputs.isEmpty()) {
            return "unknown";
        }
        Optional<FileTime> latestInput = latestModified(inputs);
        Optional<FileTime> latestOutput = latestModified(List.of(output));
        if (latestOutput.isEmpty()) {
            return "empty-output";
        }
        if (latestInput.isEmpty()) {
            return "unknown";
        }
        return latestOutput.orElseThrow().compareTo(latestInput.orElseThrow()) >= 0 ? "fresh" : "stale";
    }

    private static Optional<FileTime> latestModified(List<Path> roots) {
        List<FileTime> times = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root)) {
                readModifiedTime(root).ifPresent(times::add);
                continue;
            }
            if (Files.isDirectory(root)) {
                try (Stream<Path> paths = Files.walk(root)) {
                    paths.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .map(GeneratedSourceEvidenceService::readModifiedTime)
                            .flatMap(Optional::stream)
                            .forEach(times::add);
                } catch (IOException exception) {
                    return Optional.empty();
                }
            }
        }
        return times.stream().max(FileTime::compareTo);
    }

    private static Optional<FileTime> readModifiedTime(Path path) {
        try {
            return Optional.of(Files.getLastModifiedTime(path));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
