package com.zolt.maven.repository;

import java.util.List;
import java.util.Map;

public record EffectiveRawPom(
        RawPom rawPom,
        List<RawPom> parents,
        String groupId,
        String version,
        Map<String, String> properties,
        List<RawPomDependency> dependencyManagement) {
    public EffectiveRawPom {
        parents = List.copyOf(parents);
        properties = Map.copyOf(properties);
        dependencyManagement = List.copyOf(dependencyManagement);
    }
}
