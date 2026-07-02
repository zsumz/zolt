package com.zolt.explain.maven;

public record MavenRepositoryInspection(
        String id,
        String url,
        boolean pluginRepository,
        boolean snapshotsEnabled) {
    public MavenRepositoryInspection {
        id = id == null || id.isBlank() ? "unknown" : id;
        url = url == null ? "" : url;
    }
}
