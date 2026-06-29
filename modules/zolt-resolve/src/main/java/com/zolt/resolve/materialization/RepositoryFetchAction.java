package com.zolt.resolve.materialization;

@FunctionalInterface
interface RepositoryFetchAction {
    com.zolt.maven.RepositoryArtifact fetch(RepositoryAccess access);
}
