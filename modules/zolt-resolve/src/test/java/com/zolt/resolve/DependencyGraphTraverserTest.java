package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyGraphTraverserTest extends DependencyGraphTraverserTestSupport {
    @Test
    void resolvesGuavaFixtureShape() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.google.guava:guava:33.4.0-jre", pom("com.google.guava", "guava", "33.4.0-jre", List.of(
                dependency("com.google.guava", "failureaccess", "1.0.2"),
                dependency("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
                dependency("org.checkerframework", "checker-qual", "3.43.0"))));
        source.put("com.google.guava:failureaccess:1.0.2", pom("com.google.guava", "failureaccess", "1.0.2", List.of()));
        source.put("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava", pom("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava", List.of()));
        source.put("org.checkerframework:checker-qual:3.43.0", pom("org.checkerframework", "checker-qual", "3.43.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.google.guava", "guava", "33.4.0-jre")));

        assertEquals(List.of(
                "com.google.guava:guava:33.4.0-jre",
                "com.google.guava:failureaccess:1.0.2",
                "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                "org.checkerframework:checker-qual:3.43.0"), nodeStrings(graph));
        assertEquals(3, graph.edges().size());
    }

    @Test
    void resolvesSlf4jFixtureShape() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("org.slf4j:slf4j-api:2.0.16", pom("org.slf4j", "slf4j-api", "2.0.16", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("org.slf4j", "slf4j-api", "2.0.16")));

        assertEquals(List.of("org.slf4j:slf4j-api:2.0.16"), nodeStrings(graph));
        assertEquals(0, graph.edges().size());
    }

    @Test
    void resolvesJunitFixtureFarEnoughForTestingWork() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("org.junit.jupiter:junit-jupiter:5.11.4", pom("org.junit.jupiter", "junit-jupiter", "5.11.4", List.of(
                dependency("org.junit.jupiter", "junit-jupiter-api", "5.11.4"),
                dependency("org.junit.jupiter", "junit-jupiter-params", "5.11.4"),
                runtimeDependency("org.junit.jupiter", "junit-jupiter-engine", "5.11.4"))));
        source.put("org.junit.jupiter:junit-jupiter-api:5.11.4", pom("org.junit.jupiter", "junit-jupiter-api", "5.11.4", List.of()));
        source.put("org.junit.jupiter:junit-jupiter-params:5.11.4", pom("org.junit.jupiter", "junit-jupiter-params", "5.11.4", List.of()));
        source.put("org.junit.jupiter:junit-jupiter-engine:5.11.4", pom("org.junit.jupiter", "junit-jupiter-engine", "5.11.4", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("org.junit.jupiter", "junit-jupiter", "5.11.4")));

        assertEquals(List.of(
                "org.junit.jupiter:junit-jupiter:5.11.4",
                "org.junit.jupiter:junit-jupiter-api:5.11.4",
                "org.junit.jupiter:junit-jupiter-engine:5.11.4",
                "org.junit.jupiter:junit-jupiter-params:5.11.4"), nodeStrings(graph));
        assertEquals(DependencyScope.RUNTIME, graph.edges().get(1).request().scope());
    }

    @Test
    void avoidsInfiniteLoopsAndVisitsNodesOnce() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:a:1.0.0", pom("com.example", "a", "1.0.0", List.of(dependency("com.example", "b", "1.0.0"))));
        source.put("com.example:b:1.0.0", pom("com.example", "b", "1.0.0", List.of(dependency("com.example", "a", "1.0.0"))));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "a", "1.0.0")));

        assertEquals(List.of("com.example:a:1.0.0", "com.example:b:1.0.0"), nodeStrings(graph));
        assertEquals(2, graph.edges().size());
        assertEquals(1, source.loadCount("com.example:a:1.0.0"));
        assertEquals(1, source.loadCount("com.example:b:1.0.0"));
    }

    @Test
    void recordsVisitedNodesDeterministically() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(
                dependency("com.example", "zeta", "1.0.0"),
                dependency("com.example", "alpha", "1.0.0"))));
        source.put("com.example:alpha:1.0.0", pom("com.example", "alpha", "1.0.0", List.of()));
        source.put("com.example:zeta:1.0.0", pom("com.example", "zeta", "1.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertEquals(List.of(
                "com.example:root:1.0.0",
                "com.example:alpha:1.0.0",
                "com.example:zeta:1.0.0"), nodeStrings(graph));
    }

    @Test
    void skipsOptionalTransitiveDependencies() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(optionalDependency("com.example", "optional", "1.0.0"))));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertEquals(List.of("com.example:root:1.0.0"), nodeStrings(graph));
    }

    @Test
    void directRequestExclusionAppliesOnlyThroughDeclaringEdge() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(
                dependency("com.example", "shared", "1.0.0"),
                dependency("com.example", "excluded", "1.0.0"))));
        source.put("com.example:other:1.0.0", pom("com.example", "other", "1.0.0", List.of(
                dependency("com.example", "excluded", "1.0.0"))));
        source.put("com.example:shared:1.0.0", pom("com.example", "shared", "1.0.0", List.of()));
        source.put("com.example:excluded:1.0.0", pom("com.example", "excluded", "1.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(
                directWithExclusion("com.example", "root", "1.0.0", "com.example", "excluded"),
                direct("com.example", "other", "1.0.0")));

        assertEquals(List.of(
                "com.example:other:1.0.0",
                "com.example:root:1.0.0",
                "com.example:excluded:1.0.0",
                "com.example:shared:1.0.0"), nodeStrings(graph));
        assertEquals(List.of(
                "com.example:other:1.0.0->com.example:excluded:1.0.0",
                "com.example:root:1.0.0->com.example:shared:1.0.0"), edgeStrings(graph));
    }

    @Test
    void skipsTransitiveTestAndProvidedDependenciesFromMainGraph() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(
                testDependency("com.example", "test-helper", "1.0.0"),
                providedDependency("com.example", "provided-api", "1.0.0"))));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertEquals(List.of("com.example:root:1.0.0"), nodeStrings(graph));
    }

    @Test
    void directTestDependenciesKeepCompileAndRuntimeTransitivesInTestScope() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:test-root:1.0.0", pom("com.example", "test-root", "1.0.0", List.of(
                dependency("com.example", "compile-helper", "1.0.0"),
                runtimeDependency("com.example", "runtime-helper", "1.0.0"),
                testDependency("com.example", "test-helper", "1.0.0"))));
        source.put("com.example:compile-helper:1.0.0", pom("com.example", "compile-helper", "1.0.0", List.of()));
        source.put("com.example:runtime-helper:1.0.0", pom("com.example", "runtime-helper", "1.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(directTest("com.example", "test-root", "1.0.0")));

        assertEquals(List.of(
                "com.example:test-root:1.0.0",
                "com.example:compile-helper:1.0.0",
                "com.example:runtime-helper:1.0.0"), nodeStrings(graph));
        assertEquals(List.of(DependencyScope.TEST, DependencyScope.TEST), graph.edges().stream()
                .map(edge -> edge.request().scope())
                .toList());
    }

    @Test
    void expandsSamePackageVersionSeparatelyPerScope() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:main-root:1.0.0", pom("com.example", "main-root", "1.0.0", List.of(
                dependency("com.example", "shared", "1.0.0"))));
        source.put("com.example:test-root:1.0.0", pom("com.example", "test-root", "1.0.0", List.of(
                dependency("com.example", "shared", "1.0.0"))));
        source.put("com.example:shared:1.0.0", pom("com.example", "shared", "1.0.0", List.of(
                dependency("com.example", "shared-child", "1.0.0"))));
        source.put("com.example:shared-child:1.0.0", pom("com.example", "shared-child", "1.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(
                direct("com.example", "main-root", "1.0.0"),
                directTest("com.example", "test-root", "1.0.0")));

        assertEquals(List.of(
                "com.example:main-root:1.0.0",
                "com.example:test-root:1.0.0",
                "com.example:shared:1.0.0",
                "com.example:shared-child:1.0.0"), nodeStrings(graph));
        assertEquals(List.of(DependencyScope.COMPILE, DependencyScope.TEST), graph.edges().stream()
                .filter(edge -> edge.to().packageId().equals(new PackageId("com.example", "shared-child")))
                .map(edge -> edge.request().scope())
                .toList());
        assertEquals(1, source.loadCount("com.example:shared:1.0.0"));
    }

    @Test
    void appliesExclusionOnlyThroughDeclaringEdge() {
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        source.put("com.example:root:1.0.0", pom("com.example", "root", "1.0.0", List.of(
                dependencyWithExclusion("com.example", "left", "1.0.0", "com.example", "shared"),
                dependency("com.example", "right", "1.0.0"))));
        source.put("com.example:left:1.0.0", pom("com.example", "left", "1.0.0", List.of(dependency("com.example", "shared", "1.0.0"))));
        source.put("com.example:right:1.0.0", pom("com.example", "right", "1.0.0", List.of(dependency("com.example", "shared", "1.0.0"))));
        source.put("com.example:shared:1.0.0", pom("com.example", "shared", "1.0.0", List.of()));

        ResolutionGraph graph = traverser(source).traverse(List.of(direct("com.example", "root", "1.0.0")));

        assertEquals(List.of(
                "com.example:root:1.0.0",
                "com.example:left:1.0.0",
                "com.example:right:1.0.0",
                "com.example:shared:1.0.0"), nodeStrings(graph));
        assertEquals(List.of(
                "com.example:root:1.0.0->com.example:left:1.0.0",
                "com.example:root:1.0.0->com.example:right:1.0.0",
                "com.example:right:1.0.0->com.example:shared:1.0.0"), edgeStrings(graph));
    }

}
