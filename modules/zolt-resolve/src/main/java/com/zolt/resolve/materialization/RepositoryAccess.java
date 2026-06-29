package com.zolt.resolve.materialization;

import com.zolt.maven.RepositoryAuthentication;
import java.net.URI;
import java.util.Optional;

record RepositoryAccess(
        URI uri,
        Optional<RepositoryAuthentication> authentication) {
}
