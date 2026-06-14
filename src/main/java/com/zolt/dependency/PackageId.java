package com.zolt.dependency;

import com.zolt.maven.Coordinate;

public record PackageId(String groupId, String artifactId) {
    public static PackageId from(Coordinate coordinate) {
        return new PackageId(coordinate.groupId(), coordinate.artifactId());
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId;
    }
}
