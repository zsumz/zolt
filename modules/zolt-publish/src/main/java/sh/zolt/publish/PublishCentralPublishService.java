package sh.zolt.publish;

import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Orchestrates Sonatype Central Portal publishing: assembles the upload bundle (dry run) and, for a
 * live publish, uploads it and reports the deployment id and status. Reads {@code [publish.central]}
 * and {@code [publish.signing]} from {@code zolt.toml}; all network access is delegated to
 * {@link CentralPortalClient}.
 */
public final class PublishCentralPublishService {
    private final ZoltTomlParser projectParser;
    private final PublishSettingsReader publishSettingsReader;
    private final CentralPortalClient portalClient;
    private final CentralDeploymentWaiter waiter;
    private final Function<String, String> environment;

    public PublishCentralPublishService() {
        this(new ZoltTomlParser(), new PublishSettingsReader(), new CentralPortalClient(), System::getenv);
    }

    public PublishCentralPublishService(CentralPortalClient portalClient) {
        this(new ZoltTomlParser(), new PublishSettingsReader(), portalClient, System::getenv);
    }

    PublishCentralPublishService(
            ZoltTomlParser projectParser,
            PublishSettingsReader publishSettingsReader,
            CentralPortalClient portalClient,
            Function<String, String> environment) {
        this.projectParser = projectParser;
        this.publishSettingsReader = publishSettingsReader;
        this.portalClient = portalClient;
        this.waiter = new CentralDeploymentWaiter(portalClient);
        this.environment = environment;
    }

    /**
     * Assembles the Central bundle locally for a dry run, without any network access and without
     * invoking gpg: it lays out the artifacts, POM, and checksums so the structure can be inspected.
     * The real {@link #publish} signs every file.
     */
    public PublishCentralBundleResult assembleBundle(Path projectRoot, PublishDryRunPlan plan) {
        Path root = projectRoot.toAbsolutePath().normalize();
        return new PublishCentralBundle(PublishSigningSettings.disabled(), environment).assemble(root, plan);
    }

    /** Assembles the bundle, uploads it to the Central Portal, and returns the deployment status. */
    public PublishCentralUploadResult publish(Path projectRoot, PublishDryRunPlan plan) {
        return publish(projectRoot, plan, Optional.empty());
    }

    /**
     * Assembles the bundle, uploads it, and reports the deployment status. When {@code waitTimeout}
     * is present the deployment is polled until it reaches a terminal state (published, failed, or —
     * for user-managed deployments — validated) or the timeout elapses; when it is empty the status
     * is checked once and returned as {@link PublishCentralPublishOutcome#UPLOADED}.
     */
    public PublishCentralUploadResult publish(
            Path projectRoot, PublishDryRunPlan plan, Optional<Duration> waitTimeout) {
        Path root = projectRoot.toAbsolutePath().normalize();
        PublishSettings publish = read(root);
        PublishCentralSettings central = publish.central();
        if (!central.configured()) {
            throw new PublishException(
                    "No [publish.central] configuration found. Next: add [publish.central] with a tokenEnv "
                            + "to publish to Maven Central.");
        }
        String tokenEnv = central.tokenEnv().orElseThrow(() -> new PublishException(
                "[publish.central].tokenEnv is required to publish to Maven Central."));
        String token = environment.apply(tokenEnv);
        if (token == null || token.isBlank()) {
            throw new PublishException(
                    "Central Portal token environment variable " + tokenEnv + " is not set. Next: export "
                            + tokenEnv + " with your base64 Portal user token.");
        }
        PublishCentralBundleResult bundle = new PublishCentralBundle(publish.signing(), environment)
                .assemble(root, plan);
        String deploymentId = portalClient.upload(
                central.baseUrl(), bundle.bundlePath(), token, central.publishingType(), central.deploymentName());
        if (waitTimeout.isEmpty()) {
            CentralDeploymentStatus status = portalClient.status(central.baseUrl(), deploymentId, token);
            return new PublishCentralUploadResult(
                    bundle, deploymentId, central.publishingType(), status, PublishCentralPublishOutcome.UPLOADED);
        }
        CentralDeploymentStatus status = waiter.awaitTerminal(
                central.baseUrl(), deploymentId, token, central.publishingType(), waitTimeout.get());
        return new PublishCentralUploadResult(
                bundle, deploymentId, central.publishingType(), status, terminalOutcome(status));
    }

    private static PublishCentralPublishOutcome terminalOutcome(CentralDeploymentStatus status) {
        String state = status.state() == null ? "" : status.state().strip().toUpperCase(Locale.ROOT);
        return state.equals("PUBLISHED")
                ? PublishCentralPublishOutcome.PUBLISHED
                : PublishCentralPublishOutcome.AWAITING_MANUAL_RELEASE;
    }

    private PublishSettings read(Path root) {
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        return publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
    }
}
