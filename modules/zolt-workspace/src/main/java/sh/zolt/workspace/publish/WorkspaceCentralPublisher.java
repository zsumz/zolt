package sh.zolt.workspace.publish;

import sh.zolt.publish.CentralDeploymentStatus;
import sh.zolt.publish.CentralDeploymentWaiter;
import sh.zolt.publish.CentralPortalClient;
import sh.zolt.publish.PublishCentralBundle;
import sh.zolt.publish.PublishCentralBundleResult;
import sh.zolt.publish.PublishCentralPublishOutcome;
import sh.zolt.publish.PublishCentralSettings;
import sh.zolt.publish.PublishSettings;
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
 * {@code --dry-run} assembles and validates the bundle locally without uploading; {@code --wait}
 * polls the deployment to a terminal state through the shared {@link CentralDeploymentWaiter}.
 */
public final class WorkspaceCentralPublisher {
    private final CentralPortalClient portalClient;
    private final CentralDeploymentWaiter waiter;
    private final Function<String, String> environment;

    public WorkspaceCentralPublisher() {
        this(new CentralPortalClient());
    }

    /** Uses the CLI-configured Central Portal client (shared proxy, CA trust, and timeouts). */
    public WorkspaceCentralPublisher(CentralPortalClient portalClient) {
        this(portalClient, new CentralDeploymentWaiter(portalClient), System::getenv);
    }

    WorkspaceCentralPublisher(
            CentralPortalClient portalClient, CentralDeploymentWaiter waiter, Function<String, String> environment) {
        this.portalClient = portalClient;
        this.waiter = waiter;
        this.environment = environment;
    }

    WorkspacePublishReport publish(
            Workspace workspace, List<MemberPublication> members, WorkspacePublishService.Options options) {
        List<WorkspacePublishReport.Member> reportMembers = new ArrayList<>();
        for (MemberPublication member : members) {
            reportMembers.add(member.toReportMember());
        }
        if (members.isEmpty()) {
            return new WorkspacePublishReport(reportMembers, List.of(), false, Optional.empty(), Optional.empty());
        }
        // The whole family shares one Central + signing configuration; take it from the anchor member.
        PublishSettings publish = members.get(0).publish();

        List<PublishCentralBundle.Member> bundleMembers = new ArrayList<>();
        for (MemberPublication member : members) {
            bundleMembers.add(new PublishCentralBundle.Member(member.memberRoot(), member.plan()));
        }
        Path bundleDirectory = workspace.root().resolve("target").resolve("publish");
        PublishCentralBundleResult bundle =
                new PublishCentralBundle(publish.signing(), environment).assembleFamily(bundleDirectory, bundleMembers);

        if (options.dryRun()) {
            return new WorkspacePublishReport(reportMembers, List.of(), false, Optional.empty(), Optional.empty());
        }

        PublishCentralSettings central = publish.central();
        if (central.tokenEnv().isEmpty()) {
            return new WorkspacePublishReport(
                    reportMembers,
                    List.of("no [publish.central] configuration found for a --central family publish."),
                    false,
                    Optional.empty(),
                    Optional.empty());
        }
        String tokenEnv = central.tokenEnv().orElseThrow();
        String token = environment.apply(tokenEnv);
        if (token == null || token.isBlank()) {
            return new WorkspacePublishReport(
                    reportMembers,
                    List.of("Central Portal token environment variable " + tokenEnv + " is not set."),
                    false,
                    Optional.empty(),
                    Optional.empty());
        }
        String deploymentId = portalClient.upload(
                central.baseUrl(), bundle.bundlePath(), token, central.publishingType(), central.deploymentName());
        PublishCentralPublishOutcome outcome;
        if (options.waitTimeout().isPresent()) {
            // --wait polls to a terminal state: surface PUBLISHED vs a user-managed VALIDATED (awaiting
            // a manual release) just like the single-project path, instead of discarding the status.
            CentralDeploymentStatus status = waiter.awaitTerminal(
                    central.baseUrl(), deploymentId, token, central.publishingType(),
                    options.waitTimeout().orElseThrow());
            outcome = PublishCentralPublishOutcome.ofTerminalState(status.state());
        } else {
            outcome = PublishCentralPublishOutcome.UPLOADED;
        }
        return new WorkspacePublishReport(
                reportMembers, List.of(), List.of(), true, Optional.of(deploymentId), Optional.empty(),
                Optional.of(outcome));
    }
}
