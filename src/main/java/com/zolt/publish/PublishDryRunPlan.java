package com.zolt.publish;

import java.nio.file.Path;
import java.util.List;

public record PublishDryRunPlan(
        String coordinate,
        String versionKind,
        String repositoryId,
        String repositoryUrl,
        String artifactId,
        Path artifactPath,
        String artifactSha256,
        String artifactUploadPath,
        Path evidencePath,
        Path pomPath,
        String pomSha256,
        String pomUploadPath,
        List<String> blockers) {
    public PublishDryRunPlan {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public boolean ok() {
        return blockers.isEmpty();
    }
}
