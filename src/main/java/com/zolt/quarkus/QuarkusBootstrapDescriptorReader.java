package com.zolt.quarkus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class QuarkusBootstrapDescriptorReader {
    public QuarkusBootstrapDescriptor read(Path descriptorFile) {
        if (descriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor path is required.");
        }

        Map<String, String> properties;
        try {
            properties = properties(descriptorFile);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus bootstrap descriptor at "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again and check that target/ is readable.",
                    exception);
        }

        if (!"1".equals(properties.get("version"))) {
            throw new QuarkusAugmentationException(
                    "Unsupported Quarkus bootstrap descriptor at "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again.");
        }

        String bootstrapClass = required(properties, "bootstrapClass", descriptorFile);
        String augmentActionClass = required(properties, "augmentActionClass", descriptorFile);
        Path runtimeClasspathFile = path(properties, "runtimeClasspathFile", descriptorFile);
        Path deploymentClasspathFile = path(properties, "deploymentClasspathFile", descriptorFile);
        return new QuarkusBootstrapDescriptor(
                descriptorFile,
                runtimeClasspathFile,
                deploymentClasspathFile,
                bootstrapClass,
                augmentActionClass,
                path(properties, "projectDirectory", descriptorFile),
                path(properties, "applicationClasses", descriptorFile),
                path(properties, "augmentationDirectory", descriptorFile),
                path(properties, "packageDirectory", descriptorFile),
                required(properties, "package", descriptorFile),
                required(properties, "inputFingerprint", descriptorFile),
                classpath(runtimeClasspathFile, "runtime", descriptorFile),
                classpath(deploymentClasspathFile, "deployment", descriptorFile));
    }

    private static Map<String, String> properties(Path descriptorFile) throws IOException {
        return Files.readAllLines(descriptorFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toMap(
                        QuarkusBootstrapDescriptorReader::key,
                        QuarkusBootstrapDescriptorReader::value,
                        (left, ignored) -> left));
    }

    private static String key(String line) {
        int separator = line.indexOf('=');
        return separator == -1 ? line : line.substring(0, separator);
    }

    private static String value(String line) {
        int separator = line.indexOf('=');
        return separator == -1 ? "" : line.substring(separator + 1);
    }

    private static String required(Map<String, String> properties, String key, Path descriptorFile) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new QuarkusAugmentationException(
                    "Invalid Quarkus bootstrap descriptor at "
                            + descriptorFile
                            + ". Missing required field `"
                            + key
                            + "`.");
        }
        return value;
    }

    private static Path path(Map<String, String> properties, String key, Path descriptorFile) {
        return Path.of(required(properties, key, descriptorFile));
    }

    private static List<Path> classpath(Path classpathFile, String label, Path descriptorFile) {
        try {
            return Files.readAllLines(classpathFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(Path::of)
                    .toList();
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus "
                            + label
                            + " classpath file "
                            + classpathFile
                            + " referenced by "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again and check that target/ is readable.",
                    exception);
        }
    }
}
