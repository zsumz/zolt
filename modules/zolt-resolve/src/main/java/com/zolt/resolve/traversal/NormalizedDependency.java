package com.zolt.resolve.traversal;

import com.zolt.dependency.DependencyScope;
import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.resolve.request.DependencyExclusion;
import java.util.List;

public record NormalizedDependency(
        RawPomDependency rawDependency,
        DependencyScope scope,
        boolean optional,
        List<DependencyExclusion> exclusions) {
    public NormalizedDependency {
        exclusions = List.copyOf(exclusions);
    }

    public boolean excludes(Coordinate coordinate) {
        return exclusions.stream().anyMatch(exclusion -> exclusion.matches(coordinate));
    }
}
