package sh.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.resolve.graph.ResolutionGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class DependencyGraphTraverserGeneratedGraphTest extends DependencyGraphTraverserTestSupport {
    @Test
    void generatedAcyclicGraphsStayDeterministicAndRespectExclusions() {
        for (int seed : List.of(7, 13, 21, 34, 55)) {
            assertGeneratedGraph(seed);
        }
    }

    private void assertGeneratedGraph(int seed) {
        GeneratedGraphFixture fixture = generatedGraph(seed);
        ResolutionGraph first = traverser(fixture.source()).traverse(List.of(
                directWithExclusion("com.generated", "root", "1.0.0", "com.generated", "excluded")));
        ResolutionGraph second = traverser(fixture.source()).traverse(List.of(
                directWithExclusion("com.generated", "root", "1.0.0", "com.generated", "excluded")));

        assertEquals(nodeStrings(first), nodeStrings(second), fixture.description());
        assertEquals(edgeStrings(first), edgeStrings(second), fixture.description());
        assertEquals(new LinkedHashSet<>(nodeStrings(first)).size(), nodeStrings(first).size(), fixture.description());
        assertFalse(nodeStrings(first).contains("com.generated:excluded:1.0.0"), fixture.description());
    }

    private GeneratedGraphFixture generatedGraph(int seed) {
        Random random = new Random(seed);
        MapBackedMetadataSource source = new MapBackedMetadataSource();
        List<String> artifactIds = List.of("node-a", "node-b", "node-c", "node-d", "node-e", "node-f");
        List<String> description = new ArrayList<>();

        List<sh.zolt.maven.repository.RawPomDependency> rootDependencies = new ArrayList<>();
        rootDependencies.add(dependency("com.generated", "node-a", "1.0.0"));
        rootDependencies.add(runtimeDependency("com.generated", "node-b", "1.0.0"));
        rootDependencies.add(dependency("com.generated", "excluded", "1.0.0"));
        source.put("com.generated:root:1.0.0", pom("com.generated", "root", "1.0.0", rootDependencies));
        source.put("com.generated:excluded:1.0.0", pom("com.generated", "excluded", "1.0.0", List.of()));
        description.add("root -> node-a compile, node-b runtime, excluded compile");

        for (int index = 0; index < artifactIds.size(); index++) {
            String artifactId = artifactIds.get(index);
            List<sh.zolt.maven.repository.RawPomDependency> dependencies = new ArrayList<>();
            for (int candidate = index + 1; candidate < artifactIds.size(); candidate++) {
                if (random.nextBoolean()) {
                    String target = artifactIds.get(candidate);
                    DependencyScope scope = generatedScope(random);
                    dependencies.add(generatedDependency(scope, target));
                    description.add(artifactId + " -> " + target + " " + scope);
                }
            }
            source.put(
                    "com.generated:" + artifactId + ":1.0.0",
                    pom("com.generated", artifactId, "1.0.0", dependencies));
        }
        return new GeneratedGraphFixture(seed, source, description);
    }

    private DependencyScope generatedScope(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> DependencyScope.COMPILE;
            case 1 -> DependencyScope.RUNTIME;
            case 2 -> DependencyScope.TEST;
            default -> DependencyScope.PROVIDED;
        };
    }

    private sh.zolt.maven.repository.RawPomDependency generatedDependency(DependencyScope scope, String artifactId) {
        return switch (scope) {
            case COMPILE -> dependency("com.generated", artifactId, "1.0.0");
            case RUNTIME -> runtimeDependency("com.generated", artifactId, "1.0.0");
            case TEST -> testDependency("com.generated", artifactId, "1.0.0");
            case PROVIDED -> providedDependency("com.generated", artifactId, "1.0.0");
            default -> throw new IllegalArgumentException("Unsupported generated scope " + scope);
        };
    }

    private record GeneratedGraphFixture(int seed, MapBackedMetadataSource source, List<String> edges) {
        String description() {
            return "seed=" + seed + " graph=" + edges;
        }
    }
}
