package com.zolt.publish;

import java.nio.file.Path;
import java.util.ArrayList;
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
        List<PublishArtifactPlan> supplementalArtifacts,
        Path evidencePath,
        Path pomPath,
        String pomSha256,
        String pomUploadPath,
        String context,
        List<String> blockers) {
    public PublishDryRunPlan {
        supplementalArtifacts = supplementalArtifacts == null ? List.of() : List.copyOf(supplementalArtifacts);
        context = context == null ? "" : context;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public boolean ok() {
        return blockers.isEmpty();
    }

    public PublishDryRunPlan withContext(String context, List<String> contextBlockers) {
        List<String> combinedBlockers = new ArrayList<>(blockers);
        combinedBlockers.addAll(contextBlockers == null ? List.of() : contextBlockers);
        return new PublishDryRunPlan(
                coordinate,
                versionKind,
                repositoryId,
                repositoryUrl,
                artifactId,
                artifactPath,
                artifactSha256,
                artifactUploadPath,
                supplementalArtifacts,
                evidencePath,
                pomPath,
                pomSha256,
                pomUploadPath,
                context,
                combinedBlockers);
    }
}
