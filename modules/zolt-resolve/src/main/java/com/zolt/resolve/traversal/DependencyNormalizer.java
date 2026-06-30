package com.zolt.resolve.traversal;

import com.zolt.maven.repository.RawPomDependency;
import com.zolt.resolve.request.DependencyExclusion;
import java.util.List;

public final class DependencyNormalizer {
    private final DependencyScopeParser scopeParser;

    public DependencyNormalizer() {
        this(new DependencyScopeParser());
    }

    DependencyNormalizer(DependencyScopeParser scopeParser) {
        this.scopeParser = scopeParser;
    }

    public NormalizedDependency normalize(RawPomDependency dependency) {
        return new NormalizedDependency(
                dependency,
                scopeParser.parse(dependency),
                dependency.optional(),
                dependency.exclusions().stream()
                        .map(exclusion -> new DependencyExclusion(exclusion.groupId(), exclusion.artifactId()))
                        .toList());
    }

    public List<NormalizedDependency> normalize(List<RawPomDependency> dependencies) {
        return dependencies.stream().map(this::normalize).toList();
    }
}
