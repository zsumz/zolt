package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.repository.EffectiveRawPom;
import com.zolt.maven.repository.RawPom;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.resolve.graph.PackageNode;
import com.zolt.resolve.metrics.ResolverMetricsSink;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.request.RequestOrigin;
import com.zolt.resolve.traversal.DependencyGraphTraverser;
import com.zolt.resolve.version.VersionSelector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyGraphResolverTest {
    private final DependencyGraphResolver resolver =
            new DependencyGraphResolver(DependencyGraphTraverser::new, new VersionSelector());

    @Test
    void resolvesGraphSelectsVersionsAndRecordsMetrics() {
        Map<String, EffectiveRawPom> poms = new HashMap<>();
        poms.put(
                "com.example:app:1.0.0",
                pom(
                        "com.example",
                        "app",
                        "1.0.0",
                        List.of(dependency("com.example", "lib", "1.0.0"))));
        poms.put("com.example:lib:1.0.0", pom("com.example", "lib", "1.0.0", List.of()));
        TrackingMetrics metrics = new TrackingMetrics();

        DependencyGraphResolution result = resolver.resolve(
                coordinate -> poms.get(coordinate.toString()),
                DependencyPolicySettings.defaults(),
                Map.of(),
                List.of(new DependencyRequest(
                        new PackageId("com.example", "app"),
                        "1.0.0",
                        DependencyScope.COMPILE,
                        RequestOrigin.DIRECT)),
                metrics);

        assertEquals(List.of(
                new PackageNode(new PackageId("com.example", "app"), "1.0.0"),
                new PackageNode(new PackageId("com.example", "lib"), "1.0.0")),
                result.selection().selectedNodes());
        assertEquals(1, result.graph().edges().size());
        assertEquals(1, metrics.graphTraversalCalls);
        assertEquals(1, metrics.versionSelectionCalls);
        assertTrue(metrics.graphTraversalNanos >= 0);
        assertTrue(metrics.versionSelectionNanos >= 0);
    }

    private static EffectiveRawPom pom(
            String groupId,
            String artifactId,
            String version,
            List<RawPomDependency> dependencies) {
        RawPom rawPom = new RawPom(
                Optional.of(groupId),
                artifactId,
                Optional.of(version),
                "jar",
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of(),
                dependencies);
        return new EffectiveRawPom(rawPom, List.of(), groupId, version, Map.of(), List.of());
    }

    private static RawPomDependency dependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(
                groupId,
                artifactId,
                Optional.of(version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of());
    }

    private static final class TrackingMetrics implements ResolverMetricsSink {
        private int graphTraversalCalls;
        private int versionSelectionCalls;
        private long graphTraversalNanos;
        private long versionSelectionNanos;

        @Override
        public void addGraphTraversalNanos(long nanos) {
            graphTraversalCalls++;
            graphTraversalNanos += nanos;
        }

        @Override
        public void addVersionSelectionNanos(long nanos) {
            versionSelectionCalls++;
            versionSelectionNanos += nanos;
        }
    }
}
