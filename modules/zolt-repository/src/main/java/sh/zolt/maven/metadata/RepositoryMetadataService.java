package sh.zolt.maven.metadata;

import sh.zolt.dependency.VersionComparator;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RepositoryAccess;
import sh.zolt.maven.repository.RepositoryClientException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers a coordinate's available versions by unioning {@code maven-metadata.xml} listings across
 * the configured repositories (in the planner's alphabetical-by-id order).
 *
 * <p>Online, listings are always refetched and cached; on a transient fetch or parse failure the
 * cached listing is used with a staleness note. Offline, only the cache is consulted and a missing
 * listing yields status unknown with a note. A 404 means the artifact is simply not hosted by that
 * repository and is silently skipped. Missing metadata everywhere degrades to unknown, never an
 * error.
 */
public final class RepositoryMetadataService {
    private final MavenRepositoryClient client;
    private final MavenMetadataParser parser;
    private final MetadataCache cache;
    private final VersionComparator comparator = new VersionComparator();

    public RepositoryMetadataService(MavenRepositoryClient client, MetadataCache cache) {
        this(client, new MavenMetadataParser(), cache);
    }

    RepositoryMetadataService(MavenRepositoryClient client, MavenMetadataParser parser, MetadataCache cache) {
        this.client = client;
        this.parser = parser;
        this.cache = cache;
    }

    public MetadataDiscovery discover(
            List<RepositoryAccess> repositories, String groupId, String artifactId, boolean offline) {
        Map<String, String> sourceByVersion = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (RepositoryAccess repository : repositories) {
            contribute(repository, groupId, artifactId, offline, sourceByVersion, notes);
        }
        List<String> versions = sourceByVersion.keySet().stream().sorted(comparator).toList();
        return new MetadataDiscovery(!versions.isEmpty(), versions, sourceByVersion, notes);
    }

    private void contribute(
            RepositoryAccess repository,
            String groupId,
            String artifactId,
            boolean offline,
            Map<String, String> sourceByVersion,
            List<String> notes) {
        String repositoryId = repository.id();
        if (offline) {
            Optional<MavenMetadata> cached = readCache(repositoryId, groupId, artifactId);
            if (cached.isPresent()) {
                record(cached.orElseThrow(), repositoryId, sourceByVersion);
            } else {
                notes.add("Offline: no cached version listing for "
                        + coordinate(groupId, artifactId) + " in repository `" + repositoryId + "`.");
            }
            return;
        }
        try {
            Optional<byte[]> fresh =
                    client.fetchMetadata(repository.uri(), groupId, artifactId, repository.authentication());
            if (fresh.isEmpty()) {
                return;
            }
            MavenMetadata parsed = parser.parse(fresh.orElseThrow());
            cacheQuietly(repositoryId, groupId, artifactId, fresh.orElseThrow());
            record(parsed, repositoryId, sourceByVersion);
        } catch (RepositoryClientException | MavenMetadataParseException failure) {
            Optional<MavenMetadata> cached = readCache(repositoryId, groupId, artifactId);
            if (cached.isPresent()) {
                record(cached.orElseThrow(), repositoryId, sourceByVersion);
                notes.add(stalenessNote(repositoryId, groupId, artifactId, failure));
            } else {
                notes.add("Could not fetch version listing for "
                        + coordinate(groupId, artifactId) + " from repository `" + repositoryId
                        + "`: " + failure.getMessage());
            }
        }
    }

    private Optional<MavenMetadata> readCache(String repositoryId, String groupId, String artifactId) {
        Optional<byte[]> cached = cache.read(repositoryId, groupId, artifactId);
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parse(cached.orElseThrow()));
        } catch (MavenMetadataParseException exception) {
            return Optional.empty();
        }
    }

    private void cacheQuietly(String repositoryId, String groupId, String artifactId, byte[] bytes) {
        try {
            cache.write(repositoryId, groupId, artifactId, bytes);
        } catch (MetadataCacheException exception) {
            // Best-effort caching; discovery still returns the fresh listing.
        }
    }

    private void record(MavenMetadata metadata, String repositoryId, Map<String, String> sourceByVersion) {
        for (String version : metadata.versions()) {
            sourceByVersion.putIfAbsent(version, repositoryId);
        }
    }

    private String stalenessNote(String repositoryId, String groupId, String artifactId, RuntimeException failure) {
        String fetched = cache.fetchedAt(repositoryId, groupId, artifactId)
                .map(Instant::toString)
                .map(value -> " (fetched " + value + ")")
                .orElse("");
        return "Using cached version listing for " + coordinate(groupId, artifactId)
                + " from repository `" + repositoryId + "`" + fetched
                + "; fresh fetch failed: " + failure.getMessage();
    }

    private static String coordinate(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }
}
