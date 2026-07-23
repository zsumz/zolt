package sh.zolt.workspace.publish;

import sh.zolt.publish.CentralPortalClient;
import sh.zolt.publish.PublishCentralBundle;
import sh.zolt.publish.PublishCentralBundleResult;
import sh.zolt.publish.PublishCentralSettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSettingsReader;
import sh.zolt.workspace.service.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Phase 2 for Maven Central: assembles ONE deterministic family bundle (every member + BOM, all files
 * + checksums + signatures) and uploads it as ONE deployment. A family is a single atomic release —
 * never per-member deployments (N failure points, no atomicity, N&times; validation latency).
 * {@code --dry-run} assembles and validates the bundle locally without uploading.
 */
public final class WorkspaceCentralPublisher {
    private final PublishSettingsReader publishSettingsReader;
    private final CentralPortalClient portalClient;
    private final Function<String, String> environment;

    public WorkspaceCentralPublisher() {
        this(new PublishSettingsReader(), new CentralPortalClient(), System::getenv);
    }

    WorkspaceCentralPublisher(
            PublishSettingsReader publishSettingsReader,
            CentralPortalClient portalClient,
            Function<String, String> environment) {
        this.publishSettingsReader = publishSettingsReader;
        this.portalClient = portalClient;
        this.environment = environment;
    }

    WorkspacePublishReport publish(
            Workspace workspace,
            List<WorkspacePublishReport.Member> members,
            WorkspacePublishService.Options options) {
        if (members.isEmpty()) {
            return new WorkspacePublishReport(members, List.of(), false, Optional.empty(), Optional.empty());
        }
        // The whole family shares one Central configuration; read it from the first member.
        Path anchorRoot = workspace.root().resolve(members.get(0).memberPath());
        PublishSettings publish = publishSettingsReader.read(anchorRoot.resolve("zolt.toml"), java.util.Map.of());

        List<PublishCentralBundle.Member> bundleMembers = new ArrayList<>();
        for (WorkspacePublishReport.Member member : members) {
            bundleMembers.add(new PublishCentralBundle.Member(
                    workspace.root().resolve(member.memberPath()), member.plan()));
        }
        Path bundleDirectory = workspace.root().resolve("target").resolve("publish");
        PublishCentralBundleResult bundle =
                new PublishCentralBundle(publish.signing(), environment).assembleFamily(bundleDirectory, bundleMembers);

        if (options.dryRun()) {
            return new WorkspacePublishReport(members, List.of(), false, Optional.empty(), Optional.empty());
        }

        PublishCentralSettings central = publish.central();
        if (central.tokenEnv().isEmpty()) {
            return new WorkspacePublishReport(
                    members,
                    List.of("no [publish.central] configuration found for a --central family publish."),
                    false,
                    Optional.empty(),
                    Optional.empty());
        }
        String tokenEnv = central.tokenEnv().orElseThrow();
        String token = environment.apply(tokenEnv);
        if (token == null || token.isBlank()) {
            return new WorkspacePublishReport(
                    members,
                    List.of("Central Portal token environment variable " + tokenEnv + " is not set."),
                    false,
                    Optional.empty(),
                    Optional.empty());
        }
        String deploymentId = portalClient.upload(
                central.baseUrl(), bundle.bundlePath(), token, central.publishingType(), central.deploymentName());
        return new WorkspacePublishReport(members, List.of(), true, Optional.of(deploymentId), Optional.empty());
    }
}
