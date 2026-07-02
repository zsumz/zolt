package sh.zolt.quarkus;

import sh.zolt.quarkus.bootstrap.QuarkusArtifactKey;
import java.util.Optional;

public record QuarkusDeploymentArtifact(
        String groupId,
        String artifactId,
        Optional<String> classifier,
        String type,
        String version) {
    public QuarkusDeploymentArtifact {
        if (groupId == null || groupId.isBlank()) {
            throw new QuarkusMetadataException("Quarkus deployment artifact groupId is required.");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new QuarkusMetadataException("Quarkus deployment artifact artifactId is required.");
        }
        classifier = classifier == null ? Optional.empty() : classifier;
        if (type == null || type.isBlank()) {
            throw new QuarkusMetadataException("Quarkus deployment artifact type is required.");
        }
        if (version == null || version.isBlank()) {
            throw new QuarkusMetadataException("Quarkus deployment artifact version is required.");
        }
    }

    public static QuarkusDeploymentArtifact parse(String value) {
        String trimmed = requireValue(value);
        String[] parts = trimmed.split(":", -1);
        if (parts.length == 3) {
            return new QuarkusDeploymentArtifact(
                    requiredPart(parts[0], "groupId"),
                    requiredPart(parts[1], "artifactId"),
                    Optional.empty(),
                    "jar",
                    requiredPart(parts[2], "version"));
        }
        if (parts.length == 5) {
            return new QuarkusDeploymentArtifact(
                    requiredPart(parts[0], "groupId"),
                    requiredPart(parts[1], "artifactId"),
                    optionalPart(parts[2]),
                    requiredPart(parts[3], "type"),
                    requiredPart(parts[4], "version"));
        }
        throw new QuarkusMetadataException(
                "Malformed Quarkus deployment artifact `"
                        + value
                        + "`. Use `group:artifact:version` or `group:artifact:classifier:type:version`.");
    }

    public QuarkusArtifactKey key() {
        return new QuarkusArtifactKey(groupId, artifactId, classifier, Optional.of(type));
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new QuarkusMetadataException(
                    "Missing required Quarkus extension metadata field `deployment-artifact`.");
        }
        String trimmed = value.trim();
        if (!trimmed.equals(value) || containsWhitespace(trimmed)) {
            throw new QuarkusMetadataException(
                    "Quarkus deployment artifact `" + value + "` must not contain whitespace.");
        }
        return trimmed;
    }

    private static String requiredPart(String value, String partName) {
        if (value.isBlank()) {
            throw new QuarkusMetadataException(
                    "Malformed Quarkus deployment artifact. The " + partName + " segment is empty.");
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
        if (classifier.isPresent() || !"jar".equals(type)) {
            return groupId + ":" + artifactId + ":" + classifier.orElse("") + ":" + type + ":" + version;
        }
        return groupId + ":" + artifactId + ":" + version;
    }
}
