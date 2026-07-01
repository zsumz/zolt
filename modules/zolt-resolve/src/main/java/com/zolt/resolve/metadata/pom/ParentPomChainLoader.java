package com.zolt.resolve.metadata.pom;

import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.RawPom;
import com.zolt.maven.repository.RawPomParent;
import com.zolt.resolve.ResolveException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ParentPomChainLoader {
    List<RawPom> load(RawPom rawPom, Function<Coordinate, RawPom> rawPomLoader) {
        List<RawPom> nearestFirst = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        if (rawPom.groupId().isPresent() && rawPom.version().isPresent()) {
            visited.add(new Coordinate(
                            rawPom.groupId().orElseThrow(),
                            rawPom.artifactId(),
                            rawPom.version())
                    .toString());
        }
        RawPom current = rawPom;
        while (current.parent().isPresent()) {
            RawPomParent parent = current.parent().orElseThrow();
            Coordinate parentCoordinate = new Coordinate(
                    parent.groupId(),
                    parent.artifactId(),
                    Optional.of(parent.version()));
            String key = parentCoordinate.toString();
            if (!visited.add(key)) {
                throw new ResolveException("Parent POM cycle detected: " + String.join(" -> ", visited) + " -> " + key
                        + ". Remove the circular <parent> reference from one of these POMs.");
            }
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
