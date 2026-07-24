package sh.zolt.publish;

public final class PublishCentralUploadFormatter {
    private PublishCentralUploadFormatter() {
    }

    public static String text(PublishCentralUploadResult result) {
        StringBuilder output = new StringBuilder("Zolt publish to Maven Central\n")
                .append("Deployment id: ").append(result.deploymentId()).append('\n')
                .append("Publishing type: ").append(result.publishingType().configValue()).append('\n')
                .append("Bundle entries: ").append(result.bundle().entries().size()).append('\n');
        // The deployment state is known only when --wait polled it; a no-wait upload reports none.
        result.status().ifPresent(status ->
                output.append("Deployment state: ").append(stateOrUnknown(status.state())).append('\n'));
        return output.append(statusLine(result.outcome())).toString();
    }

    private static String statusLine(PublishCentralPublishOutcome outcome) {
        return switch (outcome) {
            case UPLOADED -> "Status: uploaded — validation continues on the Portal\n";
            case PUBLISHED -> "Status: published to Maven Central\n";
            case AWAITING_MANUAL_RELEASE -> "Status: validated — finish publishing in the Central Portal "
                    + "(https://central.sonatype.com/publishing/deployments)\n";
        };
    }

    private static String stateOrUnknown(String state) {
        return state == null || state.isBlank() ? "unknown" : state;
    }
}
