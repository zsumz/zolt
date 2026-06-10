package com.zolt.cache;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.MavenRepositoryPathBuilder;
import com.zolt.maven.RepositoryArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class LocalArtifactCache {
    private final Path root;
    private final MavenRepositoryPathBuilder pathBuilder;
    private final DownloadCoordinator downloadCoordinator;

    public LocalArtifactCache(Path root) {
        this(root, new MavenRepositoryPathBuilder(), new DownloadCoordinator());
    }

    public LocalArtifactCache(Path root, DownloadCoordinator downloadCoordinator) {
        this(root, new MavenRepositoryPathBuilder(), downloadCoordinator);
    }

    LocalArtifactCache(Path root, MavenRepositoryPathBuilder pathBuilder, DownloadCoordinator downloadCoordinator) {
        this.root = Objects.requireNonNull(root, "root");
        this.pathBuilder = Objects.requireNonNull(pathBuilder, "pathBuilder");
        this.downloadCoordinator = Objects.requireNonNull(downloadCoordinator, "downloadCoordinator");
    }

    public static Path defaultRoot() {
        return Path.of(System.getProperty("user.home"), ".zolt", "cache");
    }

    public Path pomPath(Coordinate coordinate) {
        return cachePath(pathBuilder.pomPath(coordinate));
    }

    public Path jarPath(Coordinate coordinate) {
        return cachePath(pathBuilder.jarPath(coordinate));
    }

    public Path artifactPath(ArtifactDescriptor descriptor) {
        return cachePath(pathBuilder.artifactPath(descriptor));
    }

    public CachedArtifact getOrFetchPom(Coordinate coordinate, ArtifactFetcher fetcher) {
        return getOrFetch(coordinate, pathBuilder.pomPath(coordinate), fetcher);
    }

    public CachedArtifact getOrFetchJar(Coordinate coordinate, ArtifactFetcher fetcher) {
        return getOrFetch(coordinate, pathBuilder.jarPath(coordinate), fetcher);
    }

    public CachedArtifact getOrFetchArtifact(ArtifactDescriptor descriptor, ArtifactFetcher fetcher) {
        return getOrFetch(descriptor.coordinate(), pathBuilder.artifactPath(descriptor), fetcher);
    }

    public CachedArtifact getCachedPom(Coordinate coordinate) {
        return getCached(coordinate, pathBuilder.pomPath(coordinate), "POM");
    }

    public CachedArtifact getCachedJar(Coordinate coordinate) {
        return getCached(coordinate, pathBuilder.jarPath(coordinate), "JAR");
    }

    public CachedArtifact getCachedArtifact(ArtifactDescriptor descriptor, String artifactKind) {
        return getCached(descriptor.coordinate(), pathBuilder.artifactPath(descriptor), artifactKind);
    }

    private CachedArtifact getOrFetch(Coordinate coordinate, String repositoryPath, ArtifactFetcher fetcher) {
        Path cachePath = cachePath(repositoryPath);
        if (Files.isRegularFile(cachePath)) {
            byte[] bytes = read(cachePath);
            if (bytes.length > 0) {
                return new CachedArtifact(coordinate, repositoryPath, cachePath, bytes);
            }
            throw new ArtifactCacheException(
                    "Cached artifact at " + cachePath + " is empty. Delete it and run the command again.");
        }

        return downloadCoordinator.run(repositoryPath, () -> {
            RepositoryArtifact artifact = fetcher.fetch(coordinate);
            if (artifact.bytes().length == 0) {
                throw new ArtifactCacheException(
                        "Downloaded artifact " + coordinate + " is empty. The cache was not updated.");
            }
            writeAtomically(cachePath, artifact.bytes());
            return new CachedArtifact(coordinate, repositoryPath, cachePath, artifact.bytes());
        });
    }

    private CachedArtifact getCached(Coordinate coordinate, String repositoryPath, String artifactKind) {
        Path cachePath = cachePath(repositoryPath);
        if (!Files.isRegularFile(cachePath)) {
            throw new ArtifactCacheException(
                    "Offline mode requires cached "
                            + artifactKind
                            + " for "
                            + coordinate
                            + " at "
                            + cachePath
                            + ". Run the command without --offline to download it, then retry with --offline.");
        }
        byte[] bytes = read(cachePath);
        if (bytes.length == 0) {
            throw new ArtifactCacheException(
                    "Cached artifact at " + cachePath + " is empty. Delete it and run the command again.");
        }
        return new CachedArtifact(coordinate, repositoryPath, cachePath, bytes);
    }

    private Path cachePath(String repositoryPath) {
        return root.resolve(repositoryPath).normalize();
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ArtifactCacheException(
                    "Could not read cached artifact at " + path + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) {
        Path directory = path.getParent();
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.deleteIfExists(temporary);
                throw exception;
            }
        } catch (IOException exception) {
            throw new ArtifactCacheException(
                    "Could not write cached artifact at " + path + ". Check filesystem permissions.",
                    exception);
        }
    }
}
