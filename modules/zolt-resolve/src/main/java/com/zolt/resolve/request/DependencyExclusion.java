package com.zolt.resolve.request;

import com.zolt.maven.Coordinate;

public record DependencyExclusion(String groupId, String artifactId) {
    public boolean matches(Coordinate coordinate) {
        return groupId.equals(coordinate.groupId()) && artifactId.equals(coordinate.artifactId());
    }
}
