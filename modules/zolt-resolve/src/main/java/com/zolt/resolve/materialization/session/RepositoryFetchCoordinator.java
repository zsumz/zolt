package com.zolt.resolve.materialization.session;

import com.zolt.maven.repository.RepositoryArtifact;
import com.zolt.maven.repository.RepositoryMissingArtifactException;
import com.zolt.resolve.ResolveException;
import java.util.List;

final class RepositoryFetchCoordinator {
    RepositoryArtifact fetch(List<RepositoryAccess> repositories, RepositoryFetchAction action) {
        RepositoryMissingArtifactException lastMissing = null;
        for (RepositoryAccess repository : repositories) {
            try {
                return action.fetch(repository);
            } catch (RepositoryMissingArtifactException exception) {
                lastMissing = exception;
            }
        }
        if (lastMissing != null) {
            throw ResolveException.actionable(
                    lastMissing.getMessage(),
                    "Check the group, artifact, version, and repository URL, then run `zolt resolve` again.",
                    lastMissing);
        }
        throw ResolveException.actionable(
                "No repositories are configured in zolt.toml.",
                "Add [repositories] with at least one Maven-compatible repository URL.");
    }
}
