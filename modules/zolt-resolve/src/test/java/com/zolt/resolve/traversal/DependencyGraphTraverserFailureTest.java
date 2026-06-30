package com.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
