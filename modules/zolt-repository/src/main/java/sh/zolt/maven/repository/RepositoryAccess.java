package sh.zolt.maven.repository;

import java.net.URI;
import java.util.Optional;

/**
 * A single repository to query, resolved from configuration: its stable id (for cache namespacing
 * and source attribution), its safe base URI, and any resolved authentication.
 */
public record RepositoryAccess(
        String id,
        URI uri,
        Optional<RepositoryAuthentication> authentication) {
}
