package sh.zolt.maven.repository;

import sh.zolt.maven.ArtifactDescriptor;

/**
 * Receives byte-level repository download progress without coupling repository HTTP code to resolver
 * or CLI progress abstractions.
 */
public interface RepositoryDownloadListener {
    RepositoryDownloadListener NOOP = (descriptor, received, total) -> {
    };

    void onBytes(ArtifactDescriptor descriptor, long received, long total);
}
