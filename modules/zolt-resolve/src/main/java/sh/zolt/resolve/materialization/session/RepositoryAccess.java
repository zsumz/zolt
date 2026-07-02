package sh.zolt.resolve.materialization.session;

import sh.zolt.maven.repository.RepositoryAuthentication;
import java.net.URI;
import java.util.Optional;

record RepositoryAccess(
        URI uri,
        Optional<RepositoryAuthentication> authentication) {
}
