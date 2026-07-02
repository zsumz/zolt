package sh.zolt.resolve.metadata.pom;

import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.RawPom;
import sh.zolt.resolve.traversal.GraphTraversalException;
import java.util.List;

record EffectivePomInheritanceInput(
        Coordinate requestedCoordinate,
        RawPom rawPom,
        List<RawPom> parents) {
    EffectivePomInheritanceInput {
        if (requestedCoordinate == null) {
            throw new GraphTraversalException("Effective POM inheritance requires the requested coordinate.");
        }
        if (rawPom == null) {
            throw new GraphTraversalException("Effective POM inheritance requires the raw POM.");
        }
        parents = parents == null ? List.of() : List.copyOf(parents);
    }
}
