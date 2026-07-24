package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The state line appears only when --wait polled a status; a no-wait upload prints the uploaded line. */
final class PublishCentralUploadFormatterTest {

    @Test
    void noWaitOutputPrintsDeploymentIdAndUploadedWithoutADeploymentState() {
        PublishCentralUploadResult result = new PublishCentralUploadResult(
                bundle(),
                "deployment-42",
                CentralPublishingType.AUTOMATIC,
                Optional.empty(),
                PublishCentralPublishOutcome.UPLOADED);

        String text = PublishCentralUploadFormatter.text(result);

        assertTrue(text.contains("Deployment id: deployment-42"), text);
        assertTrue(text.contains("Status: uploaded — validation continues on the Portal"), text);
        assertFalse(text.contains("Deployment state:"), text);
    }

    @Test
    void waitOutputPrintsThePolledStateAndPublishedStatus() {
        PublishCentralUploadResult result = new PublishCentralUploadResult(
                bundle(),
                "deployment-99",
                CentralPublishingType.AUTOMATIC,
                Optional.of(new CentralDeploymentStatus("deployment-99", "PUBLISHED", "{}")),
                PublishCentralPublishOutcome.PUBLISHED);

        String text = PublishCentralUploadFormatter.text(result);

        assertTrue(text.contains("Deployment state: PUBLISHED"), text);
        assertTrue(text.contains("Status: published to Maven Central"), text);
    }

    @Test
    void waitOutputForAUserManagedValidatedDeploymentRemindsToReleaseManually() {
        PublishCentralUploadResult result = new PublishCentralUploadResult(
                bundle(),
                "deployment-77",
                CentralPublishingType.USER_MANAGED,
                Optional.of(new CentralDeploymentStatus("deployment-77", "VALIDATED", "{}")),
                PublishCentralPublishOutcome.AWAITING_MANUAL_RELEASE);

        String text = PublishCentralUploadFormatter.text(result);

        assertTrue(text.contains("Deployment state: VALIDATED"), text);
        assertTrue(text.contains("Status: validated — finish publishing in the Central Portal"), text);
    }

    private static PublishCentralBundleResult bundle() {
        return new PublishCentralBundleResult(
                Path.of("target/publish/central-bundle.zip"), List.of("a.jar", "a.jar.asc"));
    }
}
