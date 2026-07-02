package com.zolt.explain.maven;

public record MavenParentInspection(
        String groupId,
        String artifactId,
        String version,
        boolean inReactor,
        boolean resolved,
        String path) {
    public MavenParentInspection {
        groupId = groupId == null ? "" : groupId;
        artifactId = artifactId == null ? "" : artifactId;
        version = version == null ? "" : version;
        path = path == null ? "" : path;
    }

    public String coordinate() {
        if (version.isBlank()) {
            return groupId + ":" + artifactId;
        }
        return groupId + ":" + artifactId + ":" + version;
    }
}
