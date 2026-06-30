package com.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.resolve.graph.ResolutionGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyGraphTraverserRelocationTest extends DependencyGraphTraverserTestSupport {
    @Test
    void resolvesDirectRelocatedDependencyToTargetCoordinate() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "io.quarkus:quarkus-junit5:3.33.2",
                relocatedPom("io.quarkus", "quarkus-junit5", "3.33.2", "io.quarkus", "quarkus-junit", "${project.version}"));
        source.put("io.quarkus:quarkus-junit:3.33.2", pom("io.quarkus", "quarkus-junit", "3.33.2", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(directTest("io.quarkus", "quarkus-junit5", "3.33.2")));

        assertEquals(List.of("io.quarkus:quarkus-junit:3.33.2"), nodeStrings(graph));
        assertEquals(1, source.loadCount("io.quarkus:quarkus-junit5:3.33.2"));
        assertEquals(1, source.loadCount("io.quarkus:quarkus-junit:3.33.2"));
    }

    @Test
    void resolvesTransitiveRelocatedDependencyToTargetCoordinate() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(
                dependency("com.legacy", "old-lib", "1.0.0"))));
        source.put(
                "com.legacy:old-lib:1.0.0",
                relocatedPom("com.legacy", "old-lib", "1.0.0", "com.modern", "new-lib", "2.0.0"));
        source.put("com.modern:new-lib:2.0.0", pom("com.modern", "new-lib", "2.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertEquals(List.of("com.example:root:1.0.0", "com.modern:new-lib:2.0.0"), nodeStrings(graph));
        assertEquals(List.of("com.example:root:1.0.0->com.modern:new-lib:2.0.0"), edgeStrings(graph));
    }

    @Test
    void relocationCycleFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "com.example:a:1.0.0",
                relocatedPom("com.example", "a", "1.0.0", "com.example", "b", "1.0.0"));
        source.put(
                "com.example:b:1.0.0",
                relocatedPom("com.example", "b", "1.0.0", "com.example", "a", "1.0.0"));

        GraphTraversalException exception = assertThrows(
                GraphTraversalException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "a", "1.0.0"))));

        assertEquals(
                "Dependency relocation cycle detected: com.example:a:1.0.0 -> com.example:b:1.0.0 -> com.example:a:1.0.0. Replace the dependency with the final relocated coordinate.",
                exception.getMessage());
    }

    @Test
    void excessiveRelocationChainFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        for (int index = 0; index < 16; index++) {
            source.put(
                    "com.example:lib-" + index + ":1.0.0",
                    relocatedPom(
                            "com.example",
                            "lib-" + index,
                            "1.0.0",
                            "com.example",
                            "lib-" + (index + 1),
                            "1.0.0"));
        }

        GraphTraversalException exception = assertThrows(
                GraphTraversalException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "lib-0", "1.0.0"))));

        assertEquals(
                "Dependency relocation chain is too deep starting at com.example:lib-0:1.0.0. Replace the dependency with the final relocated coordinate.",
                exception.getMessage());
    }
}
