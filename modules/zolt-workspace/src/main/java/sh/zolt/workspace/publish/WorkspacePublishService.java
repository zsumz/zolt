package sh.zolt.workspace.publish;

import sh.zolt.build.packaging.PackageArtifactPathPlanner;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import sh.zolt.publish.CentralPortalClient;
import sh.zolt.publish.PublishCentralReadinessService;
import sh.zolt.publish.PublishCentralRequirement;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishDryRunService;
import sh.zolt.publish.PublishInterMemberGuard;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSettingsReader;
import sh.zolt.workspace.resolve.WorkspaceMemberPolicyResolver;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceBuildPlan;
import sh.zolt.workspace.service.WorkspaceBuildService;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceSelection;
import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates {@code zolt publish --workspace}: a two-phase family publish.
 *
 * <p><strong>Phase 1 (offline).</strong> For every publishable member (in dependency order, BOM
 * last) it resolves the policy-merged config, projects a per-member publish lock (fact 6: directness
 * from config, versions from the aggregated lock), then reuses the normal single-project planner
 * ({@link PublishDryRunService#planResolved}) so every member carries the same sources/javadoc/SBOM/
 * checksum/signature plans, repository-credential and URL-safety policy as a single-project publish.
 * Inter-member completeness and per-member Central readiness are checked on top. Every blocker across
 * every member is aggregated into one family report; any blocker means nothing uploads.
 *
 * <p><strong>Phase 2.</strong> A plain repository receives a dependency-ordered sequential upload
 * (provider before consumer, BOM last) that authenticates every request and fails fast with an exact
 * resume command. Maven Central receives ONE atomic family bundle and one deployment id, polled to a
 * terminal state when {@code --wait} is set. The default enforces a uniform family version;
 * {@code allowMixedVersions} opts out.
 */
public final class WorkspacePublishService {
    private final WorkspaceBuildService workspaceBuildService;
    private final WorkspaceMemberPolicyResolver policyResolver;
    private final WorkspaceMemberPomLockProjection projection;
    private final WorkspaceBomFamily bomFamily;
    private final PublishSettingsReader publishSettingsReader;
    private final PublishCentralReadinessService centralReadinessService;
    private final PublishDryRunService dryRunService;
    private final PackageArtifactPathPlanner artifactPathPlanner = new PackageArtifactPathPlanner();
    private final WorkspaceRepositoryUploader uploader;
    private final WorkspaceCentralPublisher centralPublisher;

    public WorkspacePublishService() {
        this(new MavenRepositoryClient(), new CentralPortalClient());
    }

    /**
     * Composition-root constructor: the plain-repository uploader and the Central Portal client are
     * built from the CLI-configured {@link MavenRepositoryClient} / {@link CentralPortalClient} so
     * workspace uploads honour the same proxy, CA trust, and retry policy as single-project publishing.
     */
    public WorkspacePublishService(MavenRepositoryClient repositoryClient, CentralPortalClient portalClient) {
        this(
                new WorkspaceBuildService(),
                new WorkspaceMemberPolicyResolver(),
                new WorkspaceMemberPomLockProjection(),
                new WorkspaceBomFamily(),
                new PublishSettingsReader(),
                new PublishCentralReadinessService(),
                new PublishDryRunService(),
                new WorkspaceRepositoryUploader(repositoryClient),
                new WorkspaceCentralPublisher(portalClient));
    }

    WorkspacePublishService(
            WorkspaceBuildService workspaceBuildService,
            WorkspaceMemberPolicyResolver policyResolver,
            WorkspaceMemberPomLockProjection projection,
            WorkspaceBomFamily bomFamily,
            PublishSettingsReader publishSettingsReader,
            PublishCentralReadinessService centralReadinessService,
            PublishDryRunService dryRunService,
            WorkspaceRepositoryUploader uploader,
            WorkspaceCentralPublisher centralPublisher) {
        this.workspaceBuildService = workspaceBuildService;
        this.policyResolver = policyResolver;
        this.projection = projection;
        this.bomFamily = bomFamily;
        this.publishSettingsReader = publishSettingsReader;
        this.centralReadinessService = centralReadinessService;
        this.dryRunService = dryRunService;
        this.uploader = uploader;
        this.centralPublisher = centralPublisher;
    }

    public WorkspacePublishReport publish(
            Path startDirectory, Path cacheRoot, WorkspaceSelectionRequest selectionRequest, Options options) {
        return publish(startDirectory, cacheRoot, selectionRequest, options, WorkspaceMemberSbomGenerator.disabled());
    }

    public WorkspacePublishReport publish(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            Options options,
            WorkspaceMemberSbomGenerator sbomGenerator) {
        WorkspaceBuildPlan plan = workspaceBuildService.planBuild(startDirectory, cacheRoot, false, selectionRequest);
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
        ZoltLockfile aggregatedLock = plan.lockfile();

        List<WorkspaceMember> publishable = publishableMembers(workspace, selection);
        Set<String> publishSet = new LinkedHashSet<>();
        for (WorkspaceMember member : publishable) {
            publishSet.add(member.config().project().group() + ":" + member.config().project().name());
        }

        List<MemberPublication> publications = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        for (WorkspaceMember member : publishable) {
            MemberPlanResult result =
                    buildMemberPlan(member, workspace, aggregatedLock, publishSet, options, sbomGenerator);
            publications.add(result.publication());
            blockers.addAll(result.blockers());
        }
        if (!options.allowMixedVersions()) {
            blockers.addAll(uniformVersionBlockers(publishable));
        }

        List<WorkspacePublishReport.Member> members = new ArrayList<>();
        for (MemberPublication publication : publications) {
            members.add(publication.toReportMember());
        }

        if (!blockers.isEmpty()) {
            return new WorkspacePublishReport(members, blockers, false, Optional.empty(), Optional.empty());
        }
        if (options.central()) {
            // One atomic family bundle, one deployment id — never per-member Central deployments.
            return centralPublisher.publish(workspace, publications, options);
        }
        if (options.dryRun()) {
            return new WorkspacePublishReport(members, blockers, false, Optional.empty(), Optional.empty());
        }
        return uploader.upload(publications);
    }

    private MemberPlanResult buildMemberPlan(
            WorkspaceMember member,
            Workspace workspace,
            ZoltLockfile aggregatedLock,
            Set<String> publishSet,
            Options options,
            WorkspaceMemberSbomGenerator sbomGenerator) {
        ProjectConfig config = policyResolver.merge(workspace, member);
        boolean bom = config.packageSettings().mode() == PackageMode.BOM;
        ZoltLockfile memberLock =
                bom ? bomFamily.familyLock(workspace, aggregatedLock, member) : projection.project(config, aggregatedLock);
        PublishSettings publish =
                publishSettingsReader.read(member.directory().resolve("zolt.toml"), config.repositoryCredentials());
        Path artifactPath = bom
                ? null
                : artifactPathPlanner.jarPath(member.directory(), config).toAbsolutePath().normalize();
        Optional<Path> sbomFile = bom
                ? Optional.empty()
                : sbomGenerator.generate(member.directory(), config, memberLock);

        // Reuse the single-project planner against the projected member lock: this is the sole source
        // of the member's supplemental/SBOM/checksum plans and its credential + URL-safety blockers.
        PublishDryRunPlan memberPlan = dryRunService.planResolved(
                member.directory(),
                config,
                publish,
                () -> memberLock,
                () -> artifactPath,
                !options.central(),
                sbomFile);

        List<String> extraBlockers = new ArrayList<>();
        if (!bom) {
            for (String sibling : PublishInterMemberGuard.missingSiblings(memberLock, publishSet)) {
                extraBlockers.add("inter-member dependency `" + sibling + "` of `" + coordinateString(config)
                        + "` is not in the publish set; publish the family together or add `--member` for it.");
            }
        }
        if (options.central()) {
            for (PublishCentralRequirement requirement :
                    centralReadinessService.evaluate(config, publish, memberPlan)) {
                if (!requirement.satisfied()) {
                    extraBlockers.add(
                            coordinateString(config) + ": " + requirement.name() + " — " + requirement.remediation());
                }
            }
        }
        PublishDryRunPlan finalPlan =
                extraBlockers.isEmpty() ? memberPlan : memberPlan.withContext(memberPlan.context(), extraBlockers);
        MemberPublication publication = new MemberPublication(
                member.directory(),
                member.path(),
                coordinateString(config),
                bom,
                finalPlan,
                publish,
                config.repositoryCredentials());
        return new MemberPlanResult(publication, finalPlan.blockers());
    }

    private List<WorkspaceMember> publishableMembers(Workspace workspace, WorkspaceSelection selection) {
        Map<String, WorkspaceMember> byPath = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            byPath.put(member.path(), member);
        }
        List<WorkspaceMember> jarMembers = new ArrayList<>();
        List<WorkspaceMember> bomMembers = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = byPath.get(memberPath);
            if (member == null || !hasPublishConfig(member)) {
                continue;
            }
            if (member.config().packageSettings().mode() == PackageMode.BOM) {
                bomMembers.add(member);
            } else {
                jarMembers.add(member);
            }
        }
        // Provider before consumer (build order), BOM last so consumers can already resolve it.
        List<WorkspaceMember> ordered = new ArrayList<>(jarMembers);
        ordered.addAll(bomMembers);
        return ordered;
    }

    private boolean hasPublishConfig(WorkspaceMember member) {
        return publishSettingsReader
                .read(member.directory().resolve("zolt.toml"), member.config().repositoryCredentials())
                .configured();
    }

    private static List<String> uniformVersionBlockers(List<WorkspaceMember> publishable) {
        Set<String> versions = new LinkedHashSet<>();
        for (WorkspaceMember member : publishable) {
            versions.add(member.config().project().version());
        }
        if (versions.size() <= 1) {
            return List.of();
        }
        List<String> offenders = new ArrayList<>();
        for (WorkspaceMember member : publishable) {
            offenders.add(member.config().project().name() + "=" + member.config().project().version());
        }
        return List.of("family versions diverge (" + String.join(", ", offenders)
                + "). Align them, or pass --allow-mixed-versions to pin each member at its own version.");
    }

    private static String coordinateString(ProjectConfig config) {
        return config.project().group() + ":" + config.project().name() + ":" + config.project().version();
    }

    private record MemberPlanResult(MemberPublication publication, List<String> blockers) {
    }

    /** Publish options mirroring the CLI flags. */
    public record Options(boolean dryRun, boolean central, boolean allowMixedVersions, Optional<Duration> waitTimeout) {
        public Options {
            waitTimeout = waitTimeout == null ? Optional.empty() : waitTimeout;
        }
    }
}
