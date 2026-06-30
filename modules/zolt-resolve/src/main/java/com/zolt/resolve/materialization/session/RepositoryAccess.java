package com.zolt.resolve.materialization.session;

import com.zolt.maven.repository.RepositoryAuthentication;
import java.net.URI;
import java.util.Optional;

record RepositoryAccess(
        URI uri,
        Optional<RepositoryAuthentication> authentication) {
}
