package com.zolt.resolve.materialization.session;

@FunctionalInterface
interface RepositoryFetchAction {
    com.zolt.maven.RepositoryArtifact fetch(RepositoryAccess access);
}
