package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusMetadataException;
import java.util.Optional;

public record QuarkusArtifactKey(
        String groupId,
        String artifactId,
        Optional<String> classifier,
        Optional<String> type) {
    public QuarkusArtifactKey {
        if (groupId == null || groupId.isBlank()) {
            throw new QuarkusMetadataException("Quarkus artifact key groupId is required.");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new QuarkusMetadataException("Quarkus artifact key artifactId is required.");
        }
        classifier = classifier == null ? Optional.empty() : classifier;
        type = type == null ? Optional.empty() : type;
        if (classifier.isPresent() && type.isEmpty()) {
            throw new QuarkusMetadataException("Quarkus artifact key type is required when classifier is present.");
        }
    }

    static QuarkusArtifactKey parse(String value, String propertyName) {
        String trimmed = requireValue(value, propertyName);
        String[] parts = trimmed.split(":", -1);
        if (parts.length == 2) {
            return new QuarkusArtifactKey(
                    requiredPart(parts[0], "groupId", propertyName),
                    requiredPart(parts[1], "artifactId", propertyName),
                    Optional.empty(),
                    Optional.empty());
        }
        if (parts.length == 3) {
            return new QuarkusArtifactKey(
                    requiredPart(parts[0], "groupId", propertyName),
                    requiredPart(parts[1], "artifactId", propertyName),
                    Optional.of(requiredPart(parts[2], "classifier", propertyName)),
                    Optional.of("jar"));
        }
        if (parts.length == 4) {
            return new QuarkusArtifactKey(
                    requiredPart(parts[0], "groupId", propertyName),
                    requiredPart(parts[1], "artifactId", propertyName),
                    optionalPart(parts[2]),
                    Optional.of(requiredPart(parts[3], "type", propertyName)));
        }
        throw new QuarkusMetadataException(
                "Malformed Quarkus artifact key `"
                        + value
                        + "` in `"
                        + propertyName
                        + "`. Use `group:artifact`, `group:artifact:classifier`, or `group:artifact:classifier:type`.");
    }

    private static String requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new QuarkusMetadataException("Missing Quarkus artifact key in `" + propertyName + "`.");
        }
        String trimmed = value.trim();
        if (!trimmed.equals(value) || containsWhitespace(trimmed)) {
            throw new QuarkusMetadataException(
                    "Quarkus artifact key `"
                            + value
                            + "` in `"
                            + propertyName
                            + "` must not contain whitespace.");
        }
        return trimmed;
    }

    private static String requiredPart(String value, String partName, String propertyName) {
        if (value.isBlank()) {
            throw new QuarkusMetadataException(
                    "Malformed Quarkus artifact key in `" + propertyName + "`. The " + partName + " segment is empty.");
        }
        return value;
    }

    private static Optional<String> optionalPart(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if (classifier.isPresent() || type.isPresent()) {
            return groupId + ":" + artifactId + ":" + classifier.orElse("") + ":" + type.orElseThrow();
        }
        return groupId + ":" + artifactId;
    }
}
