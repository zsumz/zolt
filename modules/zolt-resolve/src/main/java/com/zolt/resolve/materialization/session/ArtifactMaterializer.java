package com.zolt.resolve.materialization.session;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.RepositoryArtifact;
import com.zolt.resolve.ResolveOptions;
import com.zolt.resolve.metrics.ArtifactLoadMetricsSink;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

final class ArtifactMaterializer {
    private final LocalArtifactCache cache;
    private final ResolveOptions options;
    private final LocalOverlayMaterializer localOverlayMaterializer;

    ArtifactMaterializer(
            LocalArtifactCache cache,
            ResolveOptions options,
            LocalOverlayMaterializer localOverlayMaterializer) {
        this.cache = cache;
        this.options = options;
        this.localOverlayMaterializer = localOverlayMaterializer;
    }

    CachedArtifact getPom(
            Coordinate coordinate,
            Function<Coordinate, RepositoryArtifact> fetchPom,
            ArtifactLoadMetricsSink metrics) {
        long started = System.nanoTime();
        Optional<CachedArtifact> overlayArtifact =
                localOverlayMaterializer.materializePom(options.repositoryOverlays(), coordinate);
        if (overlayArtifact.isPresent()) {
            metrics.recordPomCacheHit(elapsedSince(started));
            return overlayArtifact.orElseThrow();
        }
        if (options.offline()) {
            CachedArtifact artifact = cache.getCachedPom(coordinate);
            metrics.recordPomCacheHit(elapsedSince(started));
            return artifact;
        }
        Path before = cache.pomPath(coordinate);
        boolean cached = Files.isRegularFile(before);
        CachedArtifact artifact = cache.getOrFetchPom(coordinate, fetchPom::apply);
        if (cached) {
            metrics.recordPomCacheHit(elapsedSince(started));
        } else {
            metrics.recordPomDownload(elapsedSince(started));
        }
        return artifact;
    }

    CachedArtifact getJar(
            Coordinate coordinate,
            Function<Coordinate, RepositoryArtifact> fetchJar,
            ArtifactLoadMetricsSink metrics) {
        long started = System.nanoTime();
        Optional<CachedArtifact> overlayArtifact =
                localOverlayMaterializer.materializeArtifact(options.repositoryOverlays(), ArtifactDescriptor.jar(coordinate));
        if (overlayArtifact.isPresent()) {
            metrics.recordJarCacheHit(elapsedSince(started));
            return overlayArtifact.orElseThrow();
        }
        if (options.offline()) {
            CachedArtifact artifact = cache.getCachedJar(coordinate);
            metrics.recordJarCacheHit(elapsedSince(started));
            return artifact;
        }
        Path before = cache.jarPath(coordinate);
        boolean cached = Files.isRegularFile(before);
        CachedArtifact artifact = cache.getOrFetchJar(coordinate, fetchJar::apply);
        if (cached) {
            metrics.recordJarCacheHit(elapsedSince(started));
        } else {
            metrics.recordJarDownload(elapsedSince(started));
        }
        return artifact;
    }

    CachedArtifact getArtifact(
            ArtifactDescriptor descriptor,
            Function<ArtifactDescriptor, RepositoryArtifact> fetchArtifact,
            ArtifactLoadMetricsSink metrics) {
        long started = System.nanoTime();
        Optional<CachedArtifact> overlayArtifact =
                localOverlayMaterializer.materializeArtifact(options.repositoryOverlays(), descriptor);
        if (overlayArtifact.isPresent()) {
            metrics.recordArtifactCacheHit(elapsedSince(started));
            return overlayArtifact.orElseThrow();
        }
        if (options.offline()) {
            CachedArtifact artifact =
                    cache.getCachedArtifact(descriptor, descriptor.extension().toUpperCase(Locale.ROOT));
            metrics.recordArtifactCacheHit(elapsedSince(started));
            return artifact;
        }
        Path before = cache.artifactPath(descriptor);
        boolean cached = Files.isRegularFile(before);
        CachedArtifact artifact = cache.getOrFetchArtifact(descriptor, ignored -> fetchArtifact.apply(descriptor));
        if (cached) {
            metrics.recordArtifactCacheHit(elapsedSince(started));
        } else {
            metrics.recordArtifactDownload(elapsedSince(started));
        }
        return artifact;
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }
}
