package com.zolt.maven.repository;

import java.util.Optional;

public record RawPomParent(
        String groupId,
        String artifactId,
        String version,
        Optional<String> relativePath) {
    public RawPomParent {
        relativePath = relativePath == null ? Optional.empty() : relativePath;
    }
}
