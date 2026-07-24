package sh.zolt.publish;

import java.util.Optional;

/**
 * The outcome of a Central Portal publish: the assembled bundle, the deployment id, the outcome, and
 * the deployment status. The status is present only when {@code --wait} polled the deployment to a
 * terminal state; a no-wait upload returns immediately after the upload with an empty status and the
 * {@link PublishCentralPublishOutcome#UPLOADED} outcome, making no status request.
 */
public record PublishCentralUploadResult(
        PublishCentralBundleResult bundle,
        String deploymentId,
        CentralPublishingType publishingType,
        Optional<CentralDeploymentStatus> status,
        PublishCentralPublishOutcome outcome) {
    public PublishCentralUploadResult {
        status = status == null ? Optional.empty() : status;
    }
}
