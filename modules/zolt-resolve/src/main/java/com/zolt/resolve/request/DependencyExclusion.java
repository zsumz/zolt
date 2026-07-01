package com.zolt.resolve.request;

import com.zolt.maven.Coordinate;

public record DependencyExclusion(String groupId, String artifactId) {
    public boolean matches(Coordinate coordinate) {
        return matchesField(groupId, coordinate.groupId()) && matchesField(artifactId, coordinate.artifactId());
    }

    private static boolean matchesField(String pattern, String value) {
        return "*".equals(pattern) || pattern.equals(value);
    }
}
