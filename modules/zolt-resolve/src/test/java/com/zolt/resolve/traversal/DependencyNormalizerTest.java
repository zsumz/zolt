package com.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.maven.repository.RawPomExclusion;
import com.zolt.resolve.request.DependencyExclusion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyNormalizerTest {
    private final DependencyNormalizer normalizer = new DependencyNormalizer();

    @Test
    void normalizesRawPomDependenciesWithScopes() {
        List<NormalizedDependency> dependencies = normalizer.normalize(List.of(
                dependency("compile"),
                dependency("runtime"),
                dependency("test"),
                dependency("provided")));

        assertEquals(List.of(
                DependencyScope.COMPILE,
                DependencyScope.RUNTIME,
                DependencyScope.TEST,
                DependencyScope.PROVIDED), dependencies.stream().map(NormalizedDependency::scope).toList());
    }

    @Test
    void normalizesDependencyExclusions() {
        NormalizedDependency dependency = normalizer.normalize(new RawPomDependency(
                "com.example",
                "root",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new RawPomExclusion("com.example", "excluded"))));

        assertEquals(List.of(new DependencyExclusion("com.example", "excluded")), dependency.exclusions());
        assertTrue(dependency.excludes(new Coordinate("com.example", "excluded", Optional.of("1.0.0"))));
        assertFalse(dependency.excludes(new Coordinate("com.example", "included", Optional.of("1.0.0"))));
    }

    @Test
    void normalizesOptionalFlag() {
        NormalizedDependency dependency = normalizer.normalize(new RawPomDependency(
                "com.example",
                "optional-lib",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                true,
                List.of()));

        assertTrue(dependency.optional());
    }

    @Test
    void exclusionAppliesOnlyThroughDeclaringDependencyEdge() {
        NormalizedDependency edgeWithExclusion = normalizer.normalize(new RawPomDependency(
                "com.example",
                "left",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new RawPomExclusion("com.example", "shared"))));
        NormalizedDependency edgeWithoutExclusion = normalizer.normalize(new RawPomDependency(
                "com.example",
                "right",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of()));
        Coordinate shared = new Coordinate("com.example", "shared", Optional.of("1.0.0"));

        assertTrue(edgeWithExclusion.excludes(shared));
        assertFalse(edgeWithoutExclusion.excludes(shared));
    }

    private static RawPomDependency dependency(String scope) {
        return new RawPomDependency(
                "com.example",
                scope + "-dep",
                Optional.of("1.0.0"),
                Optional.of(scope),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of());
    }
}
