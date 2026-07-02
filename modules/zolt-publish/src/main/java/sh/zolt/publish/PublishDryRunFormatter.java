package sh.zolt.publish;

public final class PublishDryRunFormatter {
    private PublishDryRunFormatter() {
    }

    public static String text(PublishDryRunPlan plan) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt publish dry run\n");
        output.append("Coordinate: ").append(plan.coordinate()).append('\n');
        output.append("Version kind: ").append(plan.versionKind()).append('\n');
        if (!plan.context().isBlank()) {
            output.append("Context: ").append(plan.context()).append('\n');
            output.append("Policy source: built-in ").append(plan.context()).append(" context\n");
        }
        output.append("Target repository: ").append(plan.repositoryId()).append('\n');
        output.append("Target URL: ").append(plan.repositoryUrl()).append('\n');
        output.append("Artifact: ").append(plan.artifactId()).append('\n');
        output.append("Artifact path: ").append(plan.artifactPath()).append('\n');
        output.append("Artifact checksum: ").append(plan.artifactSha256()).append('\n');
        output.append("Artifact upload path: ").append(plan.artifactUploadPath()).append('\n');
        if (!plan.supplementalArtifacts().isEmpty()) {
            output.append("Supplemental artifacts:\n");
            for (PublishArtifactPlan artifact : plan.supplementalArtifacts()) {
                output.append("- ").append(artifact.id()).append(": ").append(artifact.path()).append('\n');
                output.append("  checksum: ").append(artifact.sha256()).append('\n');
                output.append("  upload path: ").append(artifact.uploadPath()).append('\n');
            }
        }
        output.append("Evidence: ").append(plan.evidencePath()).append('\n');
        output.append("Generated POM: ").append(plan.pomPath()).append('\n');
        output.append("POM checksum: ").append(plan.pomSha256()).append('\n');
        output.append("POM upload path: ").append(plan.pomUploadPath()).append('\n');
        if (plan.ok()) {
            output.append("Status: ready\n");
            output.append("No upload was performed.\n");
        } else {
            output.append("Status: blocked\n");
            for (String blocker : plan.blockers()) {
                output.append("- ").append(blocker).append('\n');
            }
        }
        return output.toString();
    }
}
