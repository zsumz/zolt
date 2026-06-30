package com.zolt.resolve.materialization.session;

@FunctionalInterface
interface RepositoryFetchAction {
    com.zolt.maven.repository.RepositoryArtifact fetch(RepositoryAccess access);
}
