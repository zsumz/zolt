package com.zolt.build;

import com.zolt.generated.GeneratedSourceEvidence;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceTokenSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class PackageEvidenceManifestWriter {
    private static final String SCHEMA = "zolt.package-evidence.v1";

    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;

    public PackageEvidenceManifestWriter() {
        this(new GeneratedSourceEvidenceService());
    }

    PackageEvidenceManifestWriter(GeneratedSourceEvidenceService generatedSourceEvidenceService) {
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
    }

    public Path write(
            Path projectDirectory,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result,
            List<PackageArtifact> artifacts) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path manifestPath = evidenceManifestPath(result.jarPath());
        try {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(
                    manifestPath,
                    json(projectRoot, config, plan, result, artifacts),
                    StandardCharsets.UTF_8);
            return manifestPath;
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not write package evidence manifest at "
                            + manifestPath
                            + ". Check that target/ is writable and retry.",
                    exception);
        }
    }

    public static Path evidenceManifestPath(Path artifactPath) {
        return artifactPath.resolveSibling(artifactPath.getFileName() + ".zolt-package.json");
    }

    private String json(
            Path projectRoot,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result,
            List<PackageArtifact> artifacts) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        stringField(json, 1, "schema", SCHEMA, true);
        project(json, config);
        json.append(",\n");
        packageInfo(json, projectRoot, config, plan, result);
        json.append(",\n");
        artifacts(json, projectRoot, result, artifacts);
        json.append(",\n");
        dependencies(json, plan.dependencies());
        json.append(",\n");
        generatedSources(json, projectRoot, config);
        json.append(",\n");
        resourceFiltering(json, projectRoot, config);
        json.append("\n}\n");
        return json.toString();
    }

    private static void project(StringBuilder json, ProjectConfig config) {
        indent(json, 1).append("\"project\": {\n");
        stringField(json, 2, "group", config.project().group(), true);
        stringField(json, 2, "name", config.project().name(), true);
        stringField(json, 2, "version", config.project().version(), true);
        nullableStringField(json, 2, "main", config.project().main(), false);
        indent(json, 1).append("}");
    }

    private static void packageInfo(
            StringBuilder json,
            Path projectRoot,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result) {
        indent(json, 1).append("\"package\": {\n");
        stringField(json, 2, "mode", result.mode().configValue(), true);
        stringField(json, 2, "archive", displayPath(projectRoot, result.jarPath()), true);
        stringField(json, 2, "applicationOutput", displayPath(projectRoot, plan.applicationOutput()), true);
        stringField(json, 2, "applicationLayout", plan.applicationLayout(), true);
        nullablePathField(json, 2, "runtimeClasspath", projectRoot, result.runtimeClasspathPath(), true);
        nullableStringField(json, 2, "startClass", config.project().main(), true);
        stringField(json, 2, "archiveSha256", sha256(result.jarPath()), false);
        indent(json, 1).append("}");
    }

    private static void artifacts(
            StringBuilder json,
            Path projectRoot,
            PackageResult result,
            List<PackageArtifact> artifacts) {
        List<ArtifactEvidence> entries = new ArrayList<>();
        entries.add(new ArtifactEvidence(
                "main",
                result.mode().configValue(),
                result.jarPath(),
                result.entryCount()));
        for (PackageArtifact artifact : artifacts.stream()
                .sorted(Comparator.comparing(PackageArtifact::classifier))
                .toList()) {
            entries.add(new ArtifactEvidence(
                    artifact.classifier(),
                    "jar",
                    artifact.path(),
                    artifact.entryCount()));
        }

        indent(json, 1).append("\"artifacts\": [");
        if (!entries.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < entries.size(); index++) {
                ArtifactEvidence entry = entries.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "classifier", entry.classifier(), true);
                stringField(json, 3, "type", entry.type(), true);
                stringField(json, 3, "path", displayPath(projectRoot, entry.path()), true);
                intField(json, 3, "entries", entry.entries(), true);
                stringField(json, 3, "sha256", sha256(entry.path()), false);
                indent(json, 2).append("}");
                if (index + 1 < entries.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void dependencies(StringBuilder json, List<PackagePlanDependency> dependencies) {
        indent(json, 1).append("\"dependencies\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                PackagePlanDependency dependency = dependencies.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "coordinate", dependency.coordinate(), true);
                stringField(json, 3, "version", dependency.version(), true);
                stringField(json, 3, "scope", dependency.scope().lockfileName(), true);
                stringArrayField(json, 3, "lanes", dependency.lanes(), true);
                booleanField(json, 3, "packageDefault", dependency.packageDefault(), true);
                stringField(json, 3, "laneDisposition", dependency.laneDisposition(), true);
                stringField(json, 3, "disposition", dependency.disposition(), true);
                stringField(json, 3, "rule", dependency.ruleName(), true);
                stringField(json, 3, "location", dependency.location(), true);
                stringField(json, 3, "reason", dependency.reason(), true);
                stringArrayField(json, 3, "policies", dependency.policies(), false);
                indent(json, 2).append("}");
                if (index + 1 < dependencies.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private void generatedSources(
            StringBuilder json,
            Path projectRoot,
            ProjectConfig config) {
        List<GeneratedSourceEvidence> generatedSources = generatedSourceEvidenceService.evidence(projectRoot, config.build());
        indent(json, 1).append("\"generatedSources\": [");
        if (!generatedSources.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < generatedSources.size(); index++) {
                GeneratedSourceEvidence evidence = generatedSources.get(index);
                GeneratedSourceStep step = evidence.step();
                indent(json, 2).append("{\n");
                stringField(json, 3, "id", evidence.id(), true);
                stringField(json, 3, "sourceRootId", evidence.sourceRootId(), true);
                stringField(json, 3, "scope", evidence.scope(), true);
                stringField(json, 3, "kind", step.kind().configValue(), true);
                stringField(json, 3, "language", step.language(), true);
                stringField(json, 3, "output", displayPath(projectRoot, evidence.output()), true);
                booleanField(json, 3, "required", step.required(), true);
                booleanField(json, 3, "clean", step.clean(), true);
                stringField(json, 3, "ownership", evidence.ownership(), true);
                stringField(json, 3, "compileLane", evidence.compileLane(), true);
                stringField(json, 3, "freshness", evidence.freshness(), true);
                stringField(json, 3, "toolArtifact", evidence.toolArtifact(), true);
                stringField(json, 3, "toolFingerprint", evidence.toolFingerprint(), true);
                stringField(json, 3, "optionsFingerprint", evidence.optionsFingerprint(), true);
                fingerprintedPaths(json, 3, "inputs", projectRoot, evidence.inputs(), false);
                indent(json, 2).append("}");
                if (index + 1 < generatedSources.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void resourceFiltering(
            StringBuilder json,
            Path projectRoot,
            ProjectConfig config) {
        ResourceFilteringSettings filtering = config.build().resourceFiltering();
        List<Path> resources = resourceInputs(projectRoot, config);
        indent(json, 1).append("\"resourceFiltering\": {\n");
        booleanField(json, 2, "enabled", filtering.enabled(), true);
        booleanField(json, 2, "testEnabled", filtering.testEnabled(), true);
        stringField(json, 2, "missing", filtering.missing().configValue(), true);
        stringArrayField(json, 2, "includes", filtering.includes(), true);
        tokenSources(json, filtering.tokens());
        json.append(",\n");
        stringField(json, 2, "fingerprint", resourceFingerprint(projectRoot, filtering, resources), true);
        fingerprintedPaths(json, 2, "inputs", projectRoot, resources, false);
        indent(json, 1).append("}");
    }

    private static void tokenSources(StringBuilder json, Map<String, ResourceTokenSettings> tokens) {
        indent(json, 2).append("\"tokenSources\": [");
        if (!tokens.isEmpty()) {
            json.append('\n');
            List<Map.Entry<String, ResourceTokenSettings>> entries = tokens.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            for (int index = 0; index < entries.size(); index++) {
                Map.Entry<String, ResourceTokenSettings> entry = entries.get(index);
                indent(json, 3).append("{\n");
                stringField(json, 4, "name", entry.getKey(), true);
                stringField(json, 4, "source", tokenSource(entry.getValue()), false);
                indent(json, 3).append("}");
                if (index + 1 < entries.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 2);
        }
        json.append("]");
    }

    private static String tokenSource(ResourceTokenSettings token) {
        if (token.value().isPresent()) {
            return "literal";
        }
        if (token.env().isPresent()) {
            return "env";
        }
        return "project";
    }

    private static List<Path> resourceInputs(Path projectRoot, ProjectConfig config) {
        List<Path> resources = new ArrayList<>();
        for (String configuredRoot : config.build().resourceRoots()) {
            Path resourceRoot = projectRoot.resolve(configuredRoot).normalize();
            if (!Files.isDirectory(resourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(resourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().endsWith(".java"))
                        .sorted(Comparator.comparing(path -> displayPath(projectRoot, path)))
                        .forEach(resources::add);
            } catch (IOException exception) {
                throw new PackageException(
                        "Could not fingerprint resources under "
                                + resourceRoot
                                + ". Check that resource files are readable and retry.",
                        exception);
            }
        }
        return List.copyOf(resources);
    }

    private static String resourceFingerprint(
            Path projectRoot,
            ResourceFilteringSettings filtering,
            List<Path> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateText(digest, "zolt-resource-filtering-v1\n");
            updateText(digest, "enabled=" + filtering.enabled() + "\n");
            updateText(digest, "testEnabled=" + filtering.testEnabled() + "\n");
            updateText(digest, "missing=" + filtering.missing().configValue() + "\n");
            for (String include : filtering.includes().stream().sorted().toList()) {
                updateText(digest, "include=" + include + "\n");
            }
            filtering.tokens().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> updateText(digest, "token=" + entry.getKey() + ":" + tokenSource(entry.getValue()) + "\n"));
            for (Path resource : resources) {
                updateText(digest, "resource=" + displayPath(projectRoot, resource) + "\n");
                digest.update(Files.readAllBytes(resource));
                updateText(digest, "\n");
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not fingerprint package resource inputs. Check that resource files are readable and retry.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PackageException("Could not fingerprint package resources because SHA-256 is unavailable.", exception);
        }
    }

    private static void fingerprintedPaths(
            StringBuilder json,
            int level,
            String name,
            Path projectRoot,
            List<Path> paths,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!paths.isEmpty()) {
            json.append('\n');
            List<Path> sorted = paths.stream()
                    .sorted(Comparator.comparing(path -> displayPath(projectRoot, path)))
                    .toList();
            for (int index = 0; index < sorted.size(); index++) {
                Path path = sorted.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "path", displayPath(projectRoot, path), true);
                stringField(json, level + 2, "sha256", fileSha256(path), false);
                indent(json, level + 1).append("}");
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, level);
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static String fileSha256(Path path) {
        if (!Files.isRegularFile(path)) {
            return "missing";
        }
        return sha256(path);
    }

    private static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence input at "
                            + path
                            + ". Check that the file is readable and retry.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PackageException("Could not compute package evidence checksum because SHA-256 is unavailable.", exception);
        }
    }

    private static void nullablePathField(
            StringBuilder json,
            int level,
            String name,
            Path projectRoot,
            Optional<Path> value,
            boolean trailingComma) {
        nullableStringField(json, level, name, value.map(path -> displayPath(projectRoot, path)), trailingComma);
    }

    private static void nullableStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        if (value.isPresent()) {
            string(json, value.orElseThrow());
        } else {
            json.append("null");
        }
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, values.get(index));
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringField(
            StringBuilder json,
            int level,
            String name,
            String value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void booleanField(
            StringBuilder json,
            int level,
            String name,
            boolean value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void intField(
            StringBuilder json,
            int level,
            String name,
            int value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static String displayPath(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString();
    }

    private static void updateText(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    private record ArtifactEvidence(String classifier, String type, Path path, int entries) {}
}
