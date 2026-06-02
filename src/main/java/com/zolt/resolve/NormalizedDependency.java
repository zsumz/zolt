package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPomDependency;
import java.util.List;

public record NormalizedDependency(
        RawPomDependency rawDependency,
        DependencyScope scope,
        List<DependencyExclusion> exclusions) {
    public NormalizedDependency {
        exclusions = List.copyOf(exclusions);
    }

    public boolean excludes(Coordinate coordinate) {
        return exclusions.stream().anyMatch(exclusion -> exclusion.matches(coordinate));
    }
}
