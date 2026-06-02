package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.maven.RawPomDependency;
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
