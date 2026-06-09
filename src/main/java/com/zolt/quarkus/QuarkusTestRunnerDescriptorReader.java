package com.zolt.quarkus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class QuarkusTestRunnerDescriptorReader {
    public QuarkusTestRunnerDescriptor read(Path descriptorFile) {
        if (descriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus test runner descriptor path is required.");
        }

        Map<String, String> properties;
        try {
            properties = properties(descriptorFile);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus test runner descriptor at "
                            + descriptorFile
                            + ". Run `zolt test` again and check that target/ is readable.",
                    exception);
        }

        if (!"1".equals(properties.get("version"))) {
            throw new QuarkusAugmentationException(
                    "Unsupported Quarkus test runner descriptor at "
                            + descriptorFile
                            + ". Run `zolt test` again.");
        }

        Path testRuntimeClasspathFile = path(properties, "testRuntimeClasspathFile", descriptorFile);
        return new QuarkusTestRunnerDescriptor(
                descriptorFile,
                testRuntimeClasspathFile,
                path(properties, "projectDirectory", descriptorFile),
                path(properties, "mainOutputDirectory", descriptorFile),
                path(properties, "testOutputDirectory", descriptorFile),
                path(properties, "serializedApplicationModel", descriptorFile),
                path(properties, "bootstrapDescriptorFile", descriptorFile),
                required(properties, "runnerMode", descriptorFile),
                booleanValue(properties, "supportsQuarkusTestAnnotations", descriptorFile),
                booleanValue(properties, "jbossLogManagerPresent", descriptorFile),
                classpath(testRuntimeClasspathFile, descriptorFile));
    }

    private static Map<String, String> properties(Path descriptorFile) throws IOException {
        return Files.readAllLines(descriptorFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toMap(
                        QuarkusTestRunnerDescriptorReader::key,
                        QuarkusTestRunnerDescriptorReader::value,
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
                    "Invalid Quarkus test runner descriptor at "
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

    private static boolean booleanValue(Map<String, String> properties, String key, Path descriptorFile) {
        String value = required(properties, key, descriptorFile);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new QuarkusAugmentationException(
                "Invalid Quarkus test runner descriptor at "
                        + descriptorFile
                        + ". Field `"
                        + key
                        + "` must be true or false.");
    }

    private static List<Path> classpath(Path classpathFile, Path descriptorFile) {
        try {
            return Files.readAllLines(classpathFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(Path::of)
                    .toList();
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus test runtime classpath file "
                            + classpathFile
                            + " referenced by "
                            + descriptorFile
                            + ". Run `zolt test` again and check that target/ is readable.",
                    exception);
        }
    }
}
