package sh.zolt.resolve.materialization.session;

@FunctionalInterface
interface RepositoryFetchAction {
    sh.zolt.maven.repository.RepositoryArtifact fetch(RepositoryAccess access);
}
