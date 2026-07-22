package sh.zolt.publish;

import java.util.Optional;

/**
 * Sonatype Central Portal configuration from {@code [publish.central]}. {@link #tokenEnv()} names an
 * environment variable holding the base64 {@code user:password} Portal token (sent as
 * {@code Authorization: Bearer <token>}). {@link #publishingType()} selects automatic vs.
 * user-managed publishing. {@link #baseUrl()} defaults to the public Portal and may be overridden
 * for an enterprise mirror. Secrets are referenced by environment-variable name only.
 */
public record PublishCentralSettings(
        boolean configured,
        Optional<String> tokenEnv,
        CentralPublishingType publishingType,
        Optional<String> deploymentName,
        String baseUrl) {
    public static final String DEFAULT_BASE_URL = "https://central.sonatype.com";

    public PublishCentralSettings {
        tokenEnv = normalize(tokenEnv);
        publishingType = publishingType == null ? CentralPublishingType.USER_MANAGED : publishingType;
        deploymentName = normalize(deploymentName);
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
    }

    public static PublishCentralSettings none() {
        return new PublishCentralSettings(
                false, Optional.empty(), CentralPublishingType.USER_MANAGED, Optional.empty(), DEFAULT_BASE_URL);
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value == null ? Optional.empty() : value.filter(candidate -> !candidate.isBlank()).map(String::trim);
    }
}
