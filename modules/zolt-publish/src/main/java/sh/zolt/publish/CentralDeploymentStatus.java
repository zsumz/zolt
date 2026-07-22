package sh.zolt.publish;

/**
 * The result of a Central Portal status query: the deployment id, its {@code deploymentState}
 * (e.g. {@code PENDING}, {@code VALIDATING}, {@code VALIDATED}, {@code PUBLISHING},
 * {@code PUBLISHED}, {@code FAILED}), and the raw response body for diagnostics.
 */
public record CentralDeploymentStatus(String deploymentId, String state, String rawResponse) {
    public CentralDeploymentStatus {
        deploymentId = deploymentId == null ? "" : deploymentId;
        state = state == null ? "" : state;
        rawResponse = rawResponse == null ? "" : rawResponse;
    }
}
