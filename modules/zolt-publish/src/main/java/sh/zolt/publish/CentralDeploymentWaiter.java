package sh.zolt.publish;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Polls the Sonatype Central Portal status endpoint until a deployment reaches a terminal state or
 * the wait budget is exhausted. {@code PUBLISHED} resolves successfully and {@code FAILED} raises an
 * actionable error carrying the Portal's reported detail. {@code VALIDATED} is terminal only for
 * {@link CentralPublishingType#USER_MANAGED} deployments — there the publisher finishes the release
 * manually in the Portal; for {@link CentralPublishingType#AUTOMATIC} it is transient and polling
 * continues. Every other state ({@code PENDING}, {@code VALIDATING}, {@code PUBLISHING}, or an
 * unrecognised value) is transient. The {@link Clock} bounds the total wait and the
 * {@link CentralPollSleeper} spaces the polls; both are injected so the loop is deterministic under
 * test.
 */
final class CentralDeploymentWaiter {
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);

    private static final String PUBLISHED = "PUBLISHED";
    private static final String FAILED = "FAILED";
    private static final String VALIDATED = "VALIDATED";
    private static final String DEPLOYMENTS_URL = "https://central.sonatype.com/publishing/deployments";

    private final CentralPortalClient portalClient;
    private final Clock clock;
    private final CentralPollSleeper sleeper;
    private final Duration pollInterval;

    CentralDeploymentWaiter(CentralPortalClient portalClient) {
        this(portalClient, Clock.systemUTC(), CentralPollSleeper.realTime(), DEFAULT_POLL_INTERVAL);
    }

    CentralDeploymentWaiter(
            CentralPortalClient portalClient, Clock clock, CentralPollSleeper sleeper, Duration pollInterval) {
        this.portalClient = portalClient;
        this.clock = clock;
        this.sleeper = sleeper;
        this.pollInterval = pollInterval;
    }

    /**
     * Polls {@code deploymentId} until it reaches a terminal state, returning that status. Raises a
     * {@link PublishException} when the deployment fails or when {@code timeout} elapses first.
     */
    CentralDeploymentStatus awaitTerminal(
            String baseUrl,
            String deploymentId,
            String token,
            CentralPublishingType publishingType,
            Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        while (true) {
            CentralDeploymentStatus status = portalClient.status(baseUrl, deploymentId, token);
            String state = normalize(status.state());
            if (PUBLISHED.equals(state)) {
                return status;
            }
            if (FAILED.equals(state)) {
                throw failed(status);
            }
            if (VALIDATED.equals(state) && publishingType == CentralPublishingType.USER_MANAGED) {
                return status;
            }
            if (!clock.instant().isBefore(deadline)) {
                throw timedOut(deploymentId, status, timeout);
            }
            pause(deploymentId);
        }
    }

    private void pause(String deploymentId) {
        try {
            sleeper.sleep(pollInterval);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublishException(
                    "Interrupted while waiting for Central deployment " + deploymentId + " to finish. "
                            + "Next: check the deployment in the Central Portal (" + DEPLOYMENTS_URL + ").",
                    exception);
        }
    }

    private static PublishException failed(CentralDeploymentStatus status) {
        String detail = status.rawResponse().isBlank() ? "" : "\n" + status.rawResponse().stripTrailing();
        return new PublishException(
                "Central Portal reported deployment " + status.deploymentId() + " as FAILED. "
                        + "Next: fix the reported validation problems and re-run zolt publish --central." + detail);
    }

    private static PublishException timedOut(String deploymentId, CentralDeploymentStatus status, Duration timeout) {
        String lastState = status.state().isBlank() ? "unknown" : status.state();
        return new PublishException(
                "Timed out after " + timeout.toSeconds() + "s waiting for Central deployment " + deploymentId
                        + " to reach a terminal state (last state: " + lastState + "). "
                        + "Next: check deployment " + deploymentId + " later in the Central Portal (" + DEPLOYMENTS_URL
                        + "), or re-run with a longer --wait-timeout.");
    }

    private static String normalize(String state) {
        return state == null ? "" : state.strip().toUpperCase(Locale.ROOT);
    }
}
