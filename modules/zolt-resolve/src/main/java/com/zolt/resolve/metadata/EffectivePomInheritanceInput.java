package com.zolt.resolve.metadata;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPom;
import com.zolt.resolve.GraphTraversalException;
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
