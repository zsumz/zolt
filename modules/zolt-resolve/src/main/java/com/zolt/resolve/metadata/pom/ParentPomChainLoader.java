package com.zolt.resolve.metadata.pom;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomParent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ParentPomChainLoader {
    List<RawPom> load(RawPom rawPom, Function<Coordinate, RawPom> rawPomLoader) {
        List<RawPom> nearestFirst = new ArrayList<>();
        RawPom current = rawPom;
        while (current.parent().isPresent()) {
            RawPomParent parent = current.parent().orElseThrow();
            Coordinate parentCoordinate = new Coordinate(
                    parent.groupId(),
                    parent.artifactId(),
                    Optional.of(parent.version()));
            RawPom parentPom = rawPomLoader.apply(parentCoordinate);
            nearestFirst.add(parentPom);
            current = parentPom;
        }
        List<RawPom> rootFirst = new ArrayList<>();
        for (int index = nearestFirst.size() - 1; index >= 0; index--) {
            rootFirst.add(nearestFirst.get(index));
        }
        return rootFirst;
    }
}
