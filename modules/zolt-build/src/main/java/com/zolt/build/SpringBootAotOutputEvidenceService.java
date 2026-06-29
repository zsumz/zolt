package com.zolt.build;

import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.booleanField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.displayPath;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.indent;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringField;

import com.zolt.build.packageevidence.PackageEvidenceChecksums;
import com.zolt.build.packageevidence.PackageEvidencePathWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class SpringBootAotOutputEvidenceService {
    private static final String SCHEMA = "zolt.spring-aot-evidence.v1";

    SpringBootAotOutputEvidence collect(Path projectRoot, String outputRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path aotRoot = root.resolve(normalizeOutputRoot(outputRoot)).resolve("spring-aot/main").normalize();
        Path sources = aotRoot.resolve("sources").normalize();
        Path classes = aotRoot.resolve("classes").normalize();
        Path resources = aotRoot.resolve("resources").normalize();
        Path metadata = resources.resolve("META-INF/native-image").normalize();
        List<Path> generatedSources = files(sources, path -> path.toString().endsWith(".java"));
        List<Path> generatedClasses = files(classes, path -> path.toString().endsWith(".class"));
        List<Path> generatedResources = files(resources, path -> true);
        List<Path> reflectionMetadata = files(metadata, path -> path.getFileName().toString().equals("reflect-config.json"));
        List<Path> reachabilityMetadata = files(metadata, path -> path.getFileName().toString().equals("reachability-metadata.json"));
        return new SpringBootAotOutputEvidence(
                aotRoot,
                sources,
                classes,
                resources,
                metadata,
                generatedSources,
                generatedClasses,
                generatedResources,
                reflectionMetadata,
                reachabilityMetadata,
                fingerprint(root, List.of(generatedSources, generatedClasses, generatedResources)));
    }

    Path write(Path projectRoot, String outputRoot, Path evidencePath) {
        Path root = projectRoot.toAbsolutePath().normalize();
        SpringBootAotOutputEvidence evidence = collect(root, outputRoot);
        try {
            Files.createDirectories(evidencePath.getParent());
            Files.writeString(evidencePath, json(root, evidence), StandardCharsets.UTF_8);
            return evidencePath;
        } catch (IOException exception) {
            throw new NativeImageException(
                    "Could not write Spring Boot AOT evidence at "
                            + evidencePath
                            + ". Check that the native output directory is writable and retry.",
                    exception);
        }
    }

    private static String json(Path projectRoot, SpringBootAotOutputEvidence evidence) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        stringField(json, 1, "schema", SCHEMA, true);
        stringField(json, 1, "outputRoot", displayPath(projectRoot, evidence.outputRoot()), true);
        stringField(json, 1, "freshness", "present", true);
        stringField(json, 1, "fingerprint", evidence.fingerprint(), true);
        directory(json, projectRoot, "sources", evidence.sourcesDirectory(), evidence.generatedSources(), true);
        directory(json, projectRoot, "classes", evidence.classesDirectory(), evidence.generatedClasses(), true);
        directory(json, projectRoot, "resources", evidence.resourcesDirectory(), evidence.generatedResources(), true);
        nativeMetadata(json, projectRoot, evidence);
        json.append("\n}\n");
        return json.toString();
    }

    private static void directory(
            StringBuilder json,
            Path projectRoot,
            String name,
            Path path,
            List<Path> files,
            boolean trailingComma) {
        indent(json, 1).append('"').append(name).append("\": {\n");
        stringField(json, 2, "path", displayPath(projectRoot, path), true);
        booleanField(json, 2, "exists", Files.isDirectory(path), true);
        PackageEvidencePathWriter.writeFingerprintedPaths(json, 2, "files", projectRoot, files, false);
        indent(json, 1).append("}");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void nativeMetadata(
            StringBuilder json,
            Path projectRoot,
            SpringBootAotOutputEvidence evidence) {
        indent(json, 1).append("\"nativeMetadata\": {\n");
        stringField(json, 2, "path", displayPath(projectRoot, evidence.nativeMetadataDirectory()), true);
        booleanField(json, 2, "exists", Files.isDirectory(evidence.nativeMetadataDirectory()), true);
        PackageEvidencePathWriter.writeFingerprintedPaths(
                json,
                2,
                "reflectionMetadata",
                projectRoot,
                evidence.reflectionMetadata(),
                true);
        PackageEvidencePathWriter.writeFingerprintedPaths(
                json,
                2,
                "reachabilityMetadata",
                projectRoot,
                evidence.reachabilityMetadata(),
                false);
        indent(json, 1).append("}");
    }

    private static List<Path> files(Path directory, Predicate<Path> predicate) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(predicate)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new NativeImageException(
                    "Could not inspect Spring Boot AOT output under "
                            + directory
                            + ". Check filesystem permissions and retry.",
                    exception);
        }
    }

    private static String fingerprint(Path projectRoot, List<List<Path>> pathGroups) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (List<Path> paths : pathGroups) {
                for (Path path : paths.stream()
                        .sorted(Comparator.comparing(p -> displayPath(projectRoot, p)))
                        .toList()) {
                    digest.update(displayPath(projectRoot, path).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(PackageEvidenceChecksums.fileSha256(path).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new NativeImageException(
                    "Could not compute Spring Boot AOT evidence fingerprint because SHA-256 is unavailable.",
                    exception);
        }
    }

    private static String normalizeOutputRoot(String outputRoot) {
        return outputRoot == null || outputRoot.isBlank() ? "target" : outputRoot;
    }
}
