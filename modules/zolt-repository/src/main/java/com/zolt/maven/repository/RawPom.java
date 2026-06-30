package com.zolt.maven.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record RawPom(
        Optional<String> groupId,
        String artifactId,
        Optional<String> version,
        String packaging,
        Optional<RawPomParent> parent,
        Optional<RawPomRelocation> relocation,
        Map<String, String> properties,
        List<RawPomDependency> dependencyManagement,
        List<RawPomDependency> dependencies) {
    public RawPom {
        groupId = groupId == null ? Optional.empty() : groupId;
        version = version == null ? Optional.empty() : version;
        parent = parent == null ? Optional.empty() : parent;
        relocation = relocation == null ? Optional.empty() : relocation;
        properties = Map.copyOf(properties);
        dependencyManagement = List.copyOf(dependencyManagement);
        dependencies = List.copyOf(dependencies);
    }
}
