package com.zolt.maven;

import java.util.Optional;

public final class MavenRepositoryPathBuilder {
    public String pomPath(Coordinate coordinate) {
        return artifactPath(coordinate, "pom");
    }

    public String jarPath(Coordinate coordinate) {
        return artifactPath(ArtifactDescriptor.jar(coordinate));
    }

    public String artifactPath(ArtifactDescriptor descriptor) {
        Coordinate coordinate = descriptor.coordinate();
        String version = coordinate.version().orElseThrow(() -> new CoordinateParseException(
                "Coordinate `" + coordinate.packageId() + "` needs a version to build a repository path."));
        String base = groupPath(coordinate)
                + "/"
                + coordinate.artifactId()
                + "/"
                + version
                + "/";
        String classifier = descriptor.classifier()
                .map(value -> "-" + value)
                .orElse("");
        return base + coordinate.artifactId() + "-" + version + classifier + "." + descriptor.extension();
    }

    public String groupPath(Coordinate coordinate) {
        return coordinate.groupId().replace('.', '/');
    }

    private String artifactPath(Coordinate coordinate, String extension) {
        return artifactPath(new ArtifactDescriptor(coordinate, Optional.empty(), extension));
    }
}
