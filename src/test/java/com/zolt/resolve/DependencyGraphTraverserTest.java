package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.maven.RawPomExclusion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyGraphTraverserTest {
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
        assertEquals(2, source.loadCount("com.example:shared:1.0.0"));
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

    private static DependencyGraphTraverser traverser(MapBackedMetadataSource source) {
        return new DependencyGraphTraverser(source);
    }

    private static DependencyRequest direct(String groupId, String artifactId, String version) {
        return new DependencyRequest(new PackageId(groupId, artifactId), version, DependencyScope.COMPILE, RequestOrigin.DIRECT);
    }

    private static DependencyRequest directTest(String groupId, String artifactId, String version) {
        return new DependencyRequest(new PackageId(groupId, artifactId), version, DependencyScope.TEST, RequestOrigin.DIRECT);
    }

    private static EffectiveRawPom pom(String groupId, String artifactId, String version, List<RawPomDependency> dependencies) {
        RawPom rawPom = new RawPom(
                Optional.of(groupId),
                artifactId,
                Optional.of(version),
                "jar",
                Optional.empty(),
                Map.of(),
                List.of(),
                dependencies);
        return new EffectiveRawPom(rawPom, List.of(), groupId, version, Map.of(), List.of());
    }

    private static RawPomDependency dependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(groupId, artifactId, Optional.of(version), Optional.empty(), Optional.empty(), Optional.empty(), false, List.of());
    }

    private static RawPomDependency runtimeDependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(groupId, artifactId, Optional.of(version), Optional.of("runtime"), Optional.empty(), Optional.empty(), false, List.of());
    }

    private static RawPomDependency testDependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(groupId, artifactId, Optional.of(version), Optional.of("test"), Optional.empty(), Optional.empty(), false, List.of());
    }

    private static RawPomDependency providedDependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(groupId, artifactId, Optional.of(version), Optional.of("provided"), Optional.empty(), Optional.empty(), false, List.of());
    }

    private static RawPomDependency optionalDependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(groupId, artifactId, Optional.of(version), Optional.empty(), Optional.empty(), Optional.empty(), true, List.of());
    }

    private static RawPomDependency versionlessDependency(String groupId, String artifactId) {
        return new RawPomDependency(groupId, artifactId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, List.of());
    }

    private static RawPomDependency dependencyWithExclusion(
            String groupId,
            String artifactId,
            String version,
            String excludedGroupId,
            String excludedArtifactId) {
        return new RawPomDependency(
                groupId,
                artifactId,
                Optional.of(version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new RawPomExclusion(excludedGroupId, excludedArtifactId)));
    }

    private static List<String> nodeStrings(ResolutionGraph graph) {
        return graph.nodes().stream()
                .map(node -> node.packageId() + ":" + node.selectedVersion())
                .toList();
    }

    private static List<String> edgeStrings(ResolutionGraph graph) {
        return graph.edges().stream()
                .map(edge -> edge.from().packageId()
                        + ":"
                        + edge.from().selectedVersion()
                        + "->"
                        + edge.to().packageId()
                        + ":"
                        + edge.to().selectedVersion())
                .toList();
    }

    private static final class MapBackedMetadataSource implements DependencyMetadataSource {
        private final Map<String, EffectiveRawPom> poms = new HashMap<>();
        private final Map<String, Integer> loadCounts = new HashMap<>();

        void put(String coordinate, EffectiveRawPom pom) {
            poms.put(coordinate, pom);
        }

        int loadCount(String coordinate) {
            return loadCounts.getOrDefault(coordinate, 0);
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            String key = coordinate.toString();
            loadCounts.merge(key, 1, Integer::sum);
            EffectiveRawPom pom = poms.get(key);
            if (pom == null) {
                throw new GraphTraversalException("No test POM for " + key);
            }
            return pom;
        }
    }
}
