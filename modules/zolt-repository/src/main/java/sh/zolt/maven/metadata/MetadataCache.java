package sh.zolt.maven.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Caches {@code maven-metadata.xml} version listings in a namespace kept strictly separate from the
 * immutable artifact cache: {@code <root>/metadata/<repoId>/<groupPath>/<artifactId>/
 * maven-metadata.xml} plus a {@code .fetched} timestamp sidecar. Version listings are mutable, so
 * they must never enter the artifact cache. Writes are atomic (temp file + atomic move).
 */
public final class MetadataCache {
    private static final String METADATA_DIR = "metadata";
    private static final String FILE_NAME = "maven-metadata.xml";
    private static final String FETCHED_SUFFIX = ".fetched";

    private final Path root;
    private final Clock clock;

    public MetadataCache(Path cacheRoot) {
        this(cacheRoot, Clock.systemUTC());
    }

    public MetadataCache(Path cacheRoot, Clock clock) {
        this.root = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<byte[]> read(String repositoryId, String groupId, String artifactId) {
        Path file = metadataFile(repositoryId, groupId, artifactId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public Optional<Instant> fetchedAt(String repositoryId, String groupId, String artifactId) {
        Path sidecar = fetchedSidecar(repositoryId, groupId, artifactId);
        if (!Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? Optional.empty() : Optional.of(Instant.parse(value));
        } catch (IOException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    public void write(String repositoryId, String groupId, String artifactId, byte[] bytes) {
        writeAtomically(metadataFile(repositoryId, groupId, artifactId), bytes);
        writeAtomically(
                fetchedSidecar(repositoryId, groupId, artifactId),
                clock.instant().toString().getBytes(StandardCharsets.UTF_8));
    }

    private Path metadataFile(String repositoryId, String groupId, String artifactId) {
        return root.resolve(METADATA_DIR)
                .resolve(sanitize(repositoryId))
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(FILE_NAME);
    }

    private Path fetchedSidecar(String repositoryId, String groupId, String artifactId) {
        Path file = metadataFile(repositoryId, groupId, artifactId);
        return file.resolveSibling(file.getFileName().toString() + FETCHED_SUFFIX);
    }

    private static String sanitize(String repositoryId) {
        StringBuilder builder = new StringBuilder(repositoryId.length());
        for (int index = 0; index < repositoryId.length(); index++) {
            char character = repositoryId.charAt(index);
            boolean safe = (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '.'
                    || character == '_'
                    || character == '-';
            builder.append(safe ? character : '_');
        }
        String result = builder.isEmpty() ? "_" : builder.toString();
        return result.equals(".") || result.equals("..") ? "_" : result;
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
            throw new MetadataCacheException(
                    "Could not write cached version listing at " + path + ". Check filesystem permissions.",
                    exception);
        }
    }
}
