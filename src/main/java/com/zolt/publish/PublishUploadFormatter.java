package com.zolt.publish;

public final class PublishUploadFormatter {
    private PublishUploadFormatter() {
    }

    public static String text(PublishUploadResult result) {
        PublishDryRunPlan plan = result.plan();
        return new StringBuilder()
                .append("Zolt publish\n")
                .append("Coordinate: ").append(plan.coordinate()).append('\n')
                .append("Target repository: ").append(plan.repositoryId()).append('\n')
                .append("Artifact uploaded: ").append(plan.artifactUploadPath()).append('\n')
                .append("POM uploaded: ").append(plan.pomUploadPath()).append('\n')
                .append("Status: uploaded\n")
                .toString();
    }
}
