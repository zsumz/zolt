package com.zolt.maven;

public final class MavenRepositoryPathBuilder {
    public String pomPath(Coordinate coordinate) {
        return artifactPath(coordinate, "pom");
    }

    public String jarPath(Coordinate coordinate) {
        return artifactPath(coordinate, "jar");
    }

    public String groupPath(Coordinate coordinate) {
        return coordinate.groupId().replace('.', '/');
    }

    private String artifactPath(Coordinate coordinate, String extension) {
        String version = coordinate.version().orElseThrow(() -> new CoordinateParseException(
                "Coordinate `" + coordinate.packageId() + "` needs a version to build a repository path."));
        String base = groupPath(coordinate)
                + "/"
                + coordinate.artifactId()
                + "/"
                + version
                + "/";
        return base + coordinate.artifactId() + "-" + version + "." + extension;
    }
}
