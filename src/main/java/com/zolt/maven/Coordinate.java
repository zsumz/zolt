package com.zolt.maven;

import java.util.Optional;

public record Coordinate(String groupId, String artifactId, Optional<String> version) {
    public Coordinate {
        version = version == null ? Optional.empty() : version;
    }

    public String packageId() {
        return groupId + ":" + artifactId;
    }

    @Override
    public String toString() {
        return version.map(value -> packageId() + ":" + value).orElseGet(this::packageId);
    }
}
