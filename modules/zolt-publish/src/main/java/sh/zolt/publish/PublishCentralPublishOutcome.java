package sh.zolt.publish;

/**
 * How a Central Portal publish concluded. {@link #UPLOADED} is the default single-check result: the
 * bundle was accepted and validation continues on the Portal. {@link #PUBLISHED} and
 * {@link #AWAITING_MANUAL_RELEASE} are only reached when {@code --wait} polled the deployment to a
 * terminal state — {@code PUBLISHED} means it is live on Maven Central, while
 * {@code AWAITING_MANUAL_RELEASE} means a user-managed deployment validated and now waits for the
 * publisher to release it from the Portal.
 */
public enum PublishCentralPublishOutcome {
    UPLOADED,
    PUBLISHED,
    AWAITING_MANUAL_RELEASE
}
