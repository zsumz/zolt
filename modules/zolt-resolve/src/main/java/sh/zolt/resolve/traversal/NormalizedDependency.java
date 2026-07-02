package sh.zolt.resolve.traversal;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.RawPomDependency;
import sh.zolt.resolve.request.DependencyExclusion;
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
