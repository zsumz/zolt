package com.zolt.cache;

import com.zolt.maven.Coordinate;
import com.zolt.maven.MavenRepositoryPathBuilder;
import com.zolt.maven.RepositoryArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LocalArtifactCache {
    private final Path root;
    private final MavenRepositoryPathBuilder pathBuilder;

    public LocalArtifactCache(Path root) {
        this(root, new MavenRepositoryPathBuilder());
    }

    LocalArtifactCache(Path root, MavenRepositoryPathBuilder pathBuilder) {
        this.root = root;
        this.pathBuilder = pathBuilder;
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

    public CachedArtifact getOrFetchPom(Coordinate coordinate, ArtifactFetcher fetcher) {
        return getOrFetch(coordinate, pathBuilder.pomPath(coordinate), fetcher);
    }

    public CachedArtifact getOrFetchJar(Coordinate coordinate, ArtifactFetcher fetcher) {
        return getOrFetch(coordinate, pathBuilder.jarPath(coordinate), fetcher);
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

        RepositoryArtifact artifact = fetcher.fetch(coordinate);
        if (artifact.bytes().length == 0) {
            throw new ArtifactCacheException(
                    "Downloaded artifact " + coordinate + " is empty. The cache was not updated.");
        }
        writeAtomically(cachePath, artifact.bytes());
        return new CachedArtifact(coordinate, repositoryPath, cachePath, artifact.bytes());
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
