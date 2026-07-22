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
                + "Status: uploaded\n";
    }

    private static String stateOrUnknown(String state) {
        return state == null || state.isBlank() ? "unknown" : state;
    }
}
