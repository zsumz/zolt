package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusDeploymentArtifact;
import com.zolt.quarkus.QuarkusMetadataException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class QuarkusExtensionMetadataReader {
    static final String METADATA_PATH = "META-INF/quarkus-extension.properties";

    public Optional<QuarkusExtensionMetadata> readIfPresent(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry metadataEntry = jar.getJarEntry(METADATA_PATH);
            if (metadataEntry == null) {
                return Optional.empty();
            }

            Properties properties = new Properties();
            try (InputStream input = jar.getInputStream(metadataEntry)) {
                properties.load(input);
            }
            return Optional.of(metadata(properties, jarPath));
        } catch (IOException exception) {
            throw new QuarkusMetadataException(
                    "Could not read Quarkus extension metadata from "
                            + jarPath
                            + ". Check that the artifact is a readable jar.",
                    exception);
        }
    }

    private static QuarkusExtensionMetadata metadata(Properties properties, Path jarPath) {
        try {
            return new QuarkusExtensionMetadata(
                    QuarkusDeploymentArtifact.parse(properties.getProperty("deployment-artifact")),
                    artifactList(properties, "parent-first-artifacts"),
                    artifactList(properties, "runner-parent-first-artifacts"),
                    artifactList(properties, "excluded-artifacts"),
                    artifactList(properties, "lesser-priority-artifacts"),
                    removedResources(properties),
                    stringList(properties, "provides-capabilities"),
                    stringList(properties, "requires-capabilities"),
                    stringList(properties, "conditional-dependencies"));
        } catch (QuarkusMetadataException exception) {
            throw new QuarkusMetadataException(
                    "Invalid Quarkus extension metadata in "
                            + jarPath
                            + ": "
                            + exception.getMessage()
                            + " Refresh the dependency cache or remove the invalid Quarkus extension dependency.",
                    exception);
        }
    }

    private static List<QuarkusArtifactKey> artifactList(Properties properties, String key) {
        return stringList(properties, key).stream()
                .map(value -> QuarkusArtifactKey.parse(value, key))
                .toList();
    }

    private static Map<QuarkusArtifactKey, List<String>> removedResources(Properties properties) {
        Map<QuarkusArtifactKey, List<String>> resources = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("removed-resources."))
                .sorted(Comparator.naturalOrder())
                .forEach(name -> {
                    String artifactKey = name.substring("removed-resources.".length());
                    List<String> values = stringList(properties, name);
                    if (!values.isEmpty()) {
                        resources.put(QuarkusArtifactKey.parse(artifactKey, name), values);
                    }
                });
        return resources;
    }

    private static List<String> stringList(Properties properties, String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        String[] parts = rawValue.split(",", -1);
        for (String part : parts) {
            String value = part.trim();
            if (value.isBlank()) {
                throw new QuarkusMetadataException(
                        "Invalid empty value in Quarkus extension metadata field `" + key + "`.");
            }
            if (containsWhitespace(value)) {
                throw new QuarkusMetadataException(
                        "Invalid whitespace in Quarkus extension metadata field `" + key + "` value `" + value + "`.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
