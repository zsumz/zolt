package com.zolt.resolve;

import com.zolt.maven.RawPomDependency;
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
        return new NormalizedDependency(dependency, scopeParser.parse(dependency));
    }

    public List<NormalizedDependency> normalize(List<RawPomDependency> dependencies) {
        return dependencies.stream().map(this::normalize).toList();
    }
}
