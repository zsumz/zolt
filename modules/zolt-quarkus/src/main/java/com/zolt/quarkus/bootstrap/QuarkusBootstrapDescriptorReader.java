package com.zolt.quarkus.bootstrap;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.quarkus.QuarkusAugmentationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        Path platformPropertiesFile = optionalPath(properties, "platformPropertiesFile");
        Path applicationModelFile = path(properties, "applicationModelFile", descriptorFile);
        return new QuarkusBootstrapDescriptor(
                descriptorFile,
                runtimeClasspathFile,
                deploymentClasspathFile,
                platformPropertiesFile,
                applicationModelFile,
                bootstrapClass,
                augmentActionClass,
                path(properties, "projectDirectory", descriptorFile),
                path(properties, "applicationClasses", descriptorFile),
                path(properties, "augmentationDirectory", descriptorFile),
                path(properties, "packageDirectory", descriptorFile),
                required(properties, "package", descriptorFile),
                required(properties, "inputFingerprint", descriptorFile),
                applicationArtifact(applicationModelFile, descriptorFile),
                classpath(runtimeClasspathFile, "runtime", descriptorFile),
                classpath(deploymentClasspathFile, "deployment", descriptorFile),
                platformProperties(platformPropertiesFile, descriptorFile),
                applicationModelDependencies(applicationModelFile, descriptorFile));
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

    private static Path optionalPath(Map<String, String> properties, String key) {
        String value = properties.get(key);
        return value == null || value.isBlank() ? Path.of("") : Path.of(value);
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

    private static List<Path> platformProperties(Path platformPropertiesFile, Path descriptorFile) {
        if (platformPropertiesFile.toString().isBlank()) {
            return List.of();
        }
        try {
            return Files.readAllLines(platformPropertiesFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(Path::of)
                    .toList();
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus platform properties file "
                            + platformPropertiesFile
                            + " referenced by "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again and check that target/ is readable.",
                    exception);
        }
    }

    private static QuarkusApplicationArtifact applicationArtifact(Path applicationModelFile, Path descriptorFile) {
        Map<String, String> properties = applicationModelProperties(applicationModelFile, descriptorFile);
        return new QuarkusApplicationArtifact(
                new PackageId(
                        required(properties, "application.groupId", applicationModelFile),
                        required(properties, "application.artifactId", applicationModelFile)),
                required(properties, "application.version", applicationModelFile),
                path(properties, "application.path", applicationModelFile));
    }

    private static List<QuarkusBootstrapDependency> applicationModelDependencies(
            Path applicationModelFile,
            Path descriptorFile) {
        Map<String, String> properties = applicationModelProperties(applicationModelFile, descriptorFile);
        int dependencyCount = intValue(properties, "dependencyCount", applicationModelFile);
        List<QuarkusBootstrapDependency> dependencies = new ArrayList<>();
        for (int index = 0; index < dependencyCount; index++) {
            dependencies.add(dependency(properties, index, applicationModelFile));
        }
        return List.copyOf(dependencies);
    }

    private static Map<String, String> applicationModelProperties(Path applicationModelFile, Path descriptorFile) {
        Map<String, String> properties;
        try {
            properties = properties(applicationModelFile);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus application model file "
                            + applicationModelFile
                            + " referenced by "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again and check that target/ is readable.",
                    exception);
        }
        if (!"1".equals(properties.get("version"))) {
            throw new QuarkusAugmentationException(
                    "Unsupported Quarkus application model file "
                            + applicationModelFile
                            + " referenced by "
                            + descriptorFile
                            + ". Run Quarkus augmentation planning again.");
        }
        return properties;
    }

    private static QuarkusBootstrapDependency dependency(
            Map<String, String> properties,
            int index,
            Path applicationModelFile) {
        String prefix = "dependency." + index + ".";
        return new QuarkusBootstrapDependency(
                new PackageId(
                        required(properties, prefix + "groupId", applicationModelFile),
                        required(properties, prefix + "artifactId", applicationModelFile)),
                required(properties, prefix + "version", applicationModelFile),
                scope(required(properties, prefix + "scope", applicationModelFile), applicationModelFile),
                path(properties, prefix + "path", applicationModelFile),
                booleanValue(properties, prefix + "direct", applicationModelFile));
    }

    private static int intValue(Map<String, String> properties, String key, Path file) {
        String value = required(properties, key, file);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new QuarkusAugmentationException(
                    "Invalid Quarkus application model at "
                            + file
                            + ". Field `"
                            + key
                            + "` must be an integer.",
                    exception);
        }
    }

    private static boolean booleanValue(Map<String, String> properties, String key, Path file) {
        String value = required(properties, key, file);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new QuarkusAugmentationException(
                "Invalid Quarkus application model at "
                        + file
                        + ". Field `"
                        + key
                        + "` must be true or false.");
    }

    private static DependencyScope scope(String value, Path applicationModelFile) {
        for (DependencyScope scope : DependencyScope.values()) {
            if (scope.lockfileName().equals(value)) {
                return scope;
            }
        }
        try {
            return DependencyScope.valueOf(value.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new QuarkusAugmentationException(
                    "Invalid Quarkus application model at "
                            + applicationModelFile
                            + ". Unsupported scope `"
                            + value
                            + "`.",
                    exception);
        }
    }
}
