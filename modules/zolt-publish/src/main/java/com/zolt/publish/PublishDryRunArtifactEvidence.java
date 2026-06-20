package com.zolt.publish;

import java.util.List;

record PublishDryRunArtifactEvidence(
        String artifactSha256,
        List<PublishArtifactPlan> supplementalArtifacts) {
}
