package com.zolt.maven.repository;

import java.util.Optional;

public record RawPomRelocation(
        Optional<String> groupId,
        Optional<String> artifactId,
        Optional<String> version,
        Optional<String> message) {
    public RawPomRelocation {
        groupId = groupId == null ? Optional.empty() : groupId;
        artifactId = artifactId == null ? Optional.empty() : artifactId;
        version = version == null ? Optional.empty() : version;
        message = message == null ? Optional.empty() : message;
    }
}
