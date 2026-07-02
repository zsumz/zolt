package sh.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.graph.ResolutionGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyGraphTraverserFailureTest extends DependencyGraphTraverserTestSupport {
    @Test
    void missingTransitiveVersionFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(versionlessDependency("com.example", "missing-version"))));

        GraphTraversalException exception = assertThrows(
                GraphTraversalException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0"))));

        assertEquals(
                "Dependency com.example:missing-version from com.example:root:1.0.0 does not declare or inherit a version.",
                exception.getMessage());
    }

    @Test
    void rangeTransitiveVersionFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "com.example:root:1.0.0",
                pom("com.example", "root", "1.0.0", List.of(dependency("com.example", "ranged", "[1.0,2.0)"))));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0"))));

        assertTrue(exception.getMessage().contains("com.example:ranged"), exception.getMessage());
        assertTrue(exception.getMessage().contains("[1.0,2.0)"), exception.getMessage());
        assertTrue(exception.getMessage().contains("com.example:root:1.0.0"), exception.getMessage());
    }

    @Test
    void snapshotTransitiveVersionFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "com.example:root:1.0.0",
                pom("com.example", "root", "1.0.0", List.of(dependency("com.example", "snap", "1.0.0-SNAPSHOT"))));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0"))));

        assertTrue(exception.getMessage().contains("com.example:snap"), exception.getMessage());
        assertTrue(exception.getMessage().contains("1.0.0-SNAPSHOT"), exception.getMessage());
        assertTrue(exception.getMessage().contains("com.example:root:1.0.0"), exception.getMessage());
    }

    @Test
    void dynamicTransitiveVersionFailsActionably() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "com.example:root:1.0.0",
                pom("com.example", "root", "1.0.0", List.of(dependency("com.example", "dynamic", "1.+"))));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0"))));

        assertTrue(exception.getMessage().contains("com.example:dynamic"), exception.getMessage());
        assertTrue(exception.getMessage().contains("1.+"), exception.getMessage());
        assertTrue(exception.getMessage().contains("com.example:root:1.0.0"), exception.getMessage());
    }

    @Test
    void fixedTransitiveVersionStillResolves() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put(
                "com.example:root:1.0.0",
                pom("com.example", "root", "1.0.0", List.of(dependency("com.example", "leaf", "2.0.0"))));
        source.put("com.example:leaf:2.0.0", pom("com.example", "leaf", "2.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertTrue(nodeStrings(graph).contains("com.example:leaf:2.0.0"), nodeStrings(graph).toString());
    }
}
