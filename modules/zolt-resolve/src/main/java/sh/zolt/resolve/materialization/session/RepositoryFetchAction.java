package sh.zolt.resolve.materialization.session;

import sh.zolt.maven.repository.RepositoryAccess;

@FunctionalInterface
interface RepositoryFetchAction {
    sh.zolt.maven.repository.RepositoryArtifact fetch(RepositoryAccess access);
}
