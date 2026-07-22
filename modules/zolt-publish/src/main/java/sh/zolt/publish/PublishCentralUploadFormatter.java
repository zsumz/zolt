package sh.zolt.publish;

public final class PublishCentralUploadFormatter {
    private PublishCentralUploadFormatter() {
    }

    public static String text(PublishCentralUploadResult result) {
        return "Zolt publish to Maven Central\n"
                + "Deployment id: " + result.deploymentId() + "\n"
                + "Publishing type: " + result.publishingType().configValue() + "\n"
                + "Bundle entries: " + result.bundle().entries().size() + "\n"
                + "Deployment state: " + stateOrUnknown(result.status().state()) + "\n"
                + statusLine(result.outcome());
    }

    private static String statusLine(PublishCentralPublishOutcome outcome) {
        return switch (outcome) {
            case UPLOADED -> "Status: uploaded\n";
            case PUBLISHED -> "Status: published to Maven Central\n";
            case AWAITING_MANUAL_RELEASE -> "Status: validated — finish publishing in the Central Portal "
                    + "(https://central.sonatype.com/publishing/deployments)\n";
        };
    }

    private static String stateOrUnknown(String state) {
        return state == null || state.isBlank() ? "unknown" : state;
    }
}
