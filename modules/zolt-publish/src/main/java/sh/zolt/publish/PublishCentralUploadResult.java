package sh.zolt.publish;

/** The outcome of a Central Portal publish: the assembled bundle, the deployment id, and status. */
public record PublishCentralUploadResult(
        PublishCentralBundleResult bundle,
        String deploymentId,
        CentralPublishingType publishingType,
        CentralDeploymentStatus status) {
}
