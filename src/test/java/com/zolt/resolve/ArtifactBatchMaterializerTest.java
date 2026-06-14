package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cache.CachedArtifact;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ArtifactBatchMaterializerTest {
    private final ArtifactBatchMaterializer materializer = new ArtifactBatchMaterializer();

    @Test
    void materializesUniqueArtifactsInStableDescriptorOrder() {
        ArtifactDescriptor zeta = jar("com.example", "zeta", "1.0.0");
        ArtifactDescriptor alpha = jar("com.example", "alpha", "1.0.0");
        Map<ArtifactDescriptor, AtomicInteger> calls = new ConcurrentHashMap<>();

        Map<ArtifactDescriptor, CachedArtifact> artifacts = materializer.materialize(
                List.of(zeta, alpha, zeta),
                2,
                descriptor -> {
                    calls.computeIfAbsent(descriptor, ignored -> new AtomicInteger()).incrementAndGet();
                    return cached(descriptor);
                });

        assertEquals(List.of(alpha, zeta), new ArrayList<>(artifacts.keySet()));
        assertEquals(1, calls.get(alpha).get());
        assertEquals(1, calls.get(zeta).get());
    }

    @Test
    void returnsEmptyMapWithoutCallingMaterializerForEmptyInput() {
        Map<ArtifactDescriptor, CachedArtifact> artifacts = materializer.materialize(
                List.of(),
                1,
                descriptor -> {
                    throw new AssertionError("unexpected materializer call");
                });

        assertEquals(Map.of(), artifacts);
    }

    @Test
    void reportsFailuresInStableDescriptorOrder() {
        ArtifactDescriptor zeta = jar("com.example", "zeta", "1.0.0");
        ArtifactDescriptor alpha = jar("com.example", "alpha", "1.0.0");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> materializer.materialize(
                        List.of(zeta, alpha),
                        2,
                        descriptor -> {
                            throw new ResolveException("missing " + descriptor.coordinate().artifactId());
                        }));

        String message = exception.getMessage();
        assertEquals("Selected artifact downloads failed:", message.lines().findFirst().orElseThrow());
        int alphaIndex = message.indexOf("com.example:alpha:1.0.0::jar");
        int zetaIndex = message.indexOf("com.example:zeta:1.0.0::jar");
        assertTrue(alphaIndex >= 0);
        assertTrue(zetaIndex >= 0);
        assertTrue(alphaIndex < zetaIndex);
        assertTrue(message.contains("Retry the command or check your repository and network settings."));
    }

    private static ArtifactDescriptor jar(String groupId, String artifactId, String version) {
        return ArtifactDescriptor.jar(new Coordinate(groupId, artifactId, Optional.of(version)));
    }

    private static CachedArtifact cached(ArtifactDescriptor descriptor) {
        return new CachedArtifact(
                descriptor.coordinate(),
                descriptor.coordinate().toString(),
                Path.of(descriptor.coordinate().artifactId()),
                new byte[] {1});
    }
}
