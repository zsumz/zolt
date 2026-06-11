package com.zolt.generated;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
                ownership(step),
                scope.equals("main") ? "main-compile" : "test-compile",
                toolArtifact(step),
                toolFingerprint(step),
                optionsFingerprint(step));
    }

    private static String ownership(GeneratedSourceStep step) {
        if (step.kind() == GeneratedSourceKind.OPENAPI) {
            return "zolt-owned-openapi";
        }
        return step.clean() ? "zolt-owned-clean" : "external-declared-root";
    }

    private static String toolArtifact(GeneratedSourceStep step) {
        if (step.kind() != GeneratedSourceKind.OPENAPI) {
            return "";
        }
        OpenApiGenerationSettings settings = step.openApi();
        if (settings.toolCoordinate().isEmpty() || settings.toolVersion().isEmpty()) {
            return "missing";
        }
        return settings.toolCoordinate().orElseThrow() + ":" + settings.toolVersion().orElseThrow();
    }

    private static String toolFingerprint(GeneratedSourceStep step) {
        if (step.kind() != GeneratedSourceKind.OPENAPI) {
            return "";
        }
        return sha256("tool\n" + toolArtifact(step) + "\n");
    }

    private static String optionsFingerprint(GeneratedSourceStep step) {
        if (step.kind() != GeneratedSourceKind.OPENAPI) {
            return "";
        }
        OpenApiGenerationSettings settings = step.openApi();
        StringBuilder canonical = new StringBuilder();
        canonical.append("openapi-options\n");
        line(canonical, "preset", settings.preset());
        line(canonical, "generator", settings.generator());
        line(canonical, "library", settings.library());
        line(canonical, "apiPackage", settings.apiPackage());
        line(canonical, "modelPackage", settings.modelPackage());
        line(canonical, "invokerPackage", settings.invokerPackage());
        line(canonical, "config", settings.config());
        line(canonical, "templateDir", settings.templateDir());
        map(canonical, "options", settings.options());
        map(canonical, "globalProperties", settings.globalProperties());
        map(canonical, "typeMappings", settings.typeMappings());
        map(canonical, "importMappings", settings.importMappings());
        return sha256(canonical.toString());
    }

    private static void line(StringBuilder canonical, String key, Optional<String> value) {
        canonical.append(key).append('=').append(value.orElse("")).append('\n');
    }

    private static void map(StringBuilder canonical, String name, Map<String, String> values) {
        canonical.append('[').append(name).append(']').append('\n');
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
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
