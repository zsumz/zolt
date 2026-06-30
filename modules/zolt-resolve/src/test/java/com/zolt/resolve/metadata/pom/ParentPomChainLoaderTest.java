package com.zolt.resolve.metadata.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomParent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ParentPomChainLoaderTest {
    private final ParentPomChainLoader loader = new ParentPomChainLoader();

    @Test
    void loadsParentChainAndReturnsRootFirst() {
        RawPom child = pom("child", parent("com.example", "nearest-parent", "2.0.0"));
        RawPom nearest = pom("nearest-parent", parent("com.example", "root-parent", "1.0.0"));
        RawPom root = pom("root-parent", Optional.empty());
        List<Coordinate> requested = new ArrayList<>();

        List<RawPom> parents = loader.load(child, coordinate -> {
            requested.add(coordinate);
            if (coordinate.artifactId().equals("nearest-parent")) {
                return nearest;
            }
            if (coordinate.artifactId().equals("root-parent")) {
                return root;
            }
            throw new AssertionError("unexpected parent coordinate " + coordinate);
        });

        assertEquals(List.of(
                new Coordinate("com.example", "nearest-parent", Optional.of("2.0.0")),
                new Coordinate("com.example", "root-parent", Optional.of("1.0.0"))), requested);
        assertEquals(List.of(root, nearest), parents);
    }

    @Test
    void returnsEmptyListWhenPomHasNoParent() {
        assertEquals(List.of(), loader.load(pom("child", Optional.empty()), coordinate -> {
            throw new AssertionError("unexpected parent load");
        }));
    }

    private static RawPom pom(String artifactId, Optional<RawPomParent> parent) {
        return new RawPom(
                Optional.of("com.example"),
                artifactId,
                Optional.of("1.0.0"),
                "pom",
                parent,
                Optional.empty(),
                Map.of(),
                List.of(),
                List.of());
    }

    private static Optional<RawPomParent> parent(String groupId, String artifactId, String version) {
        return Optional.of(new RawPomParent(groupId, artifactId, version, Optional.empty()));
    }
}
