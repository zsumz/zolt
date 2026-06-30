package com.zolt.resolve.metadata.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.concurrent.RepositoryExecutionLane;
import com.zolt.maven.Coordinate;
import com.zolt.resolve.ResolveException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PomMetadataPreloaderTest {
    private final PomMetadataPreloader preloader = new PomMetadataPreloader();

    @Test
    void preloadsUniqueCoordinatesInStableOrder() {
        Coordinate zeta = coordinate("com.example", "zeta", "1.0.0");
        Coordinate alpha = coordinate("com.example", "alpha", "1.0.0");
        List<Coordinate> loaded = Collections.synchronizedList(new ArrayList<>());

        preloader.preload(
                List.of(zeta, alpha, zeta),
                1,
                coordinate -> {
                    loaded.add(coordinate);
                    return null;
                });

        assertEquals(List.of(alpha, zeta), loaded);
    }

    @Test
    void returnsWithoutCallingLoaderForEmptyInput() {
        preloader.preload(
                List.of(),
                1,
                coordinate -> {
                    throw new AssertionError("unexpected preload call");
                });
    }

    @Test
    void preloadsWithSelectedExecutionLane() {
        Coordinate alpha = coordinate("com.example", "alpha", "1.0.0");
        List<Boolean> virtualThreads = Collections.synchronizedList(new ArrayList<>());

        preloader.preload(
                List.of(alpha),
                1,
                RepositoryExecutionLane.VIRTUAL,
                coordinate -> {
                    virtualThreads.add(Thread.currentThread().isVirtual());
                    return null;
                });

        assertEquals(List.of(true), virtualThreads);
    }

    @Test
    void reportsFailuresInStableCoordinateOrder() {
        Coordinate zeta = coordinate("com.example", "zeta", "1.0.0");
        Coordinate alpha = coordinate("com.example", "alpha", "1.0.0");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> preloader.preload(
                        List.of(zeta, alpha),
                        2,
                        coordinate -> {
                            throw new ResolveException("missing " + coordinate.artifactId());
                        }));

        String message = exception.getMessage();
        assertEquals("POM metadata fetch failed:", message.lines().findFirst().orElseThrow());
        int alphaIndex = message.indexOf("com.example:alpha:1.0.0");
        int zetaIndex = message.indexOf("com.example:zeta:1.0.0");
        assertTrue(alphaIndex >= 0);
        assertTrue(zetaIndex >= 0);
        assertTrue(alphaIndex < zetaIndex);
        assertTrue(message.contains("Retry the command or check your repository and network settings."));
    }

    private static Coordinate coordinate(String groupId, String artifactId, String version) {
        return new Coordinate(groupId, artifactId, Optional.of(version));
    }
}
