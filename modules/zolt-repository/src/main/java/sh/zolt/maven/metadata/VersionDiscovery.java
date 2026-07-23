package sh.zolt.maven.metadata;

import sh.zolt.maven.repository.RepositoryAccess;
import java.util.List;

/**
 * Discovers a coordinate's available versions across repositories. Implemented by {@link
 * RepositoryMetadataService}; expressed as an interface so advisory callers (such as the update
 * engine) can depend on discovery without binding to the HTTP-backed implementation.
 */
public interface VersionDiscovery {
    MetadataDiscovery discover(List<RepositoryAccess> repositories, String groupId, String artifactId, boolean offline);
}
