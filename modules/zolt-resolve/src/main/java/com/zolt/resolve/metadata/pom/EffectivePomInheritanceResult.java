package com.zolt.resolve.metadata.pom;

import com.zolt.maven.repository.EffectiveRawPom;
import com.zolt.maven.repository.RawPom;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.resolve.traversal.GraphTraversalException;
import java.util.List;
import java.util.Map;

record EffectivePomInheritanceResult(
        String groupId,
        String version,
        Map<String, String> properties,
        List<RawPomDependency> dependencyManagement) {
    EffectivePomInheritanceResult {
        if (groupId == null || groupId.isBlank()) {
            throw new GraphTraversalException("Effective POM groupId is required.");
        }
        if (version == null || version.isBlank()) {
            throw new GraphTraversalException("Effective POM version is required.");
        }
        properties = Map.copyOf(properties);
        dependencyManagement = List.copyOf(dependencyManagement);
    }

    EffectiveRawPom toEffectiveRawPom(RawPom rawPom, List<RawPom> parents) {
        return new EffectiveRawPom(
                rawPom,
                parents,
                groupId,
                version,
                properties,
                dependencyManagement);
    }
}
