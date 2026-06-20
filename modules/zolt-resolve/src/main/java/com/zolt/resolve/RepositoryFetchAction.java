package com.zolt.resolve;

@FunctionalInterface
interface RepositoryFetchAction {
    com.zolt.maven.RepositoryArtifact fetch(RepositoryAccess access);
}
