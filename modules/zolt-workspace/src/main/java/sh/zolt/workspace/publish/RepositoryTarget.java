package sh.zolt.workspace.publish;

import sh.zolt.maven.repository.RepositoryAuthentication;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A member's fully-resolved plain-repository destination, materialized and validated in Phase 1 so
 * Phase 2 never re-derives it: either a local {@code file:} directory (written in Maven layout) or a
 * remote {@code http(s)} URI with its resolved credentials. Remote targets have already passed the
 * repository-URL policy (valid scheme, no embedded credentials, HTTPS when credentialed, plain HTTP
 * only for loopback) — the same policy the single-project uploader applies per request.
 */
record RepositoryTarget(boolean local, Path directory, URI uri, Optional<RepositoryAuthentication> authentication) {
    static RepositoryTarget local(Path directory) {
        return new RepositoryTarget(true, directory, null, Optional.empty());
    }

    static RepositoryTarget remote(URI uri, Optional<RepositoryAuthentication> authentication) {
        return new RepositoryTarget(false, null, uri, authentication);
    }

    /**
     * A stable, comparable identity for this destination, recorded in the resume manifest so a resume
     * can refuse when the repository target changed between attempts: the resolved {@code http(s)} URL
     * for a remote target, or {@code file:} plus the canonical absolute path for a local one.
     * Credentials are never part of it — the URL already carries none by policy.
     */
    String identity() {
        return local ? "file:" + directory.toAbsolutePath().normalize() : uri.toString();
    }
}
