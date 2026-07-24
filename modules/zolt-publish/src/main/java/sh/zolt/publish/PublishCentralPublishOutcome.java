package sh.zolt.publish;

import java.util.Locale;

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
    AWAITING_MANUAL_RELEASE;

    /**
     * Maps a polled terminal deployment state to its outcome: {@code PUBLISHED} is live on Central,
     * every other terminal state (a user-managed {@code VALIDATED}) awaits a manual release. Shared by
     * the single-project and workspace-family Central paths so both surface the same terminal status.
     */
    public static PublishCentralPublishOutcome ofTerminalState(String state) {
        String normalized = state == null ? "" : state.strip().toUpperCase(Locale.ROOT);
        return normalized.equals("PUBLISHED") ? PUBLISHED : AWAITING_MANUAL_RELEASE;
    }
}
