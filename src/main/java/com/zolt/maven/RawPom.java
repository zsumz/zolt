package com.zolt.maven;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record RawPom(
        Optional<String> groupId,
        String artifactId,
        Optional<String> version,
        String packaging,
        Optional<RawPomParent> parent,
        Map<String, String> properties,
        List<RawPomDependency> dependencyManagement,
        List<RawPomDependency> dependencies) {
    public RawPom {
        groupId = groupId == null ? Optional.empty() : groupId;
        version = version == null ? Optional.empty() : version;
        parent = parent == null ? Optional.empty() : parent;
        properties = Map.copyOf(properties);
        dependencyManagement = List.copyOf(dependencyManagement);
        dependencies = List.copyOf(dependencies);
    }
}
