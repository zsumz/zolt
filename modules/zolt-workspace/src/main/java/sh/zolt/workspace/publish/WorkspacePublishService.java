package sh.zolt.workspace.publish;

import sh.zolt.build.packageplan.PackagePlanService;
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
import java.util.LinkedHashSet;
import java.util.List;
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
    // Framework-aware archive resolution: the composition root injects the framework package-plan rules
    // so a member's real primary artifact is planned as single-project publishing plans it (see below).
    private final PackagePlanService packagePlanService;
    // SBOM generation needs the member's FULL closure (transitive components, hashes, edges); the POM
    // projection above is direct-only and hash-less, so the per-member SBOM is projected separately.
    private final WorkspaceMemberSbomLockProjection sbomProjection = new WorkspaceMemberSbomLockProjection();
    private final WorkspaceRepositoryUploader uploader;
    private final WorkspaceCentralPublisher centralPublisher;
    private final WorkspacePublishStaging staging;

    public WorkspacePublishService() {
        this(new MavenRepositoryClient(), new CentralPortalClient(), new PackagePlanService());
    }

    public WorkspacePublishService(MavenRepositoryClient repositoryClient, CentralPortalClient portalClient) {
        this(repositoryClient, portalClient, new PackagePlanService());
    }

    /**
     * Composition-root constructor: the plain-repository uploader and the Central Portal client are
     * built from the CLI-configured {@link MavenRepositoryClient} / {@link CentralPortalClient} so
     * workspace uploads honour the same proxy, CA trust, and retry policy as single-project publishing.
     * {@code packagePlanService} carries the framework package-plan rules so each member's real primary
     * artifact is planned framework-aware (a Quarkus fast-jar's runner jar, a WAR's archive) rather than
     * synthesized as {@code <name>-<version>.jar}; the no-rules default suffices for plain jar members.
     */
    public WorkspacePublishService(
            MavenRepositoryClient repositoryClient,
            CentralPortalClient portalClient,
            PackagePlanService packagePlanService) {
        this(
                new WorkspaceBuildService(),
                new WorkspaceMemberPolicyResolver(),
                new WorkspaceMemberPomLockProjection(),
                new WorkspaceBomFamily(),
                new PublishSettingsReader(),
                new PublishCentralReadinessService(),
                new PublishDryRunService(),
                new WorkspaceRepositoryUploader(repositoryClient),
                new WorkspaceCentralPublisher(portalClient),
                packagePlanService);
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
            WorkspaceCentralPublisher centralPublisher,
            PackagePlanService packagePlanService) {
        this.workspaceBuildService = workspaceBuildService;
        this.policyResolver = policyResolver;
        this.projection = projection;
        this.bomFamily = bomFamily;
        this.publishSettingsReader = publishSettingsReader;
        this.centralReadinessService = centralReadinessService;
        this.dryRunService = dryRunService;
        this.uploader = uploader;
        this.centralPublisher = centralPublisher;
        this.packagePlanService = packagePlanService;
        this.staging = new WorkspacePublishStaging();
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
        boolean resumeMode = selectionRequest.exact();

        // A `--resume-members` publish is backed by durable state, not a trusted hidden flag: without
        // matching state we refuse rather than silently treat absent providers as already published.
        Path statePath = WorkspacePublishPaths.resumeStatePath(workspace);
        Optional<ResumeState> resumeState = resumeMode ? ResumeState.read(statePath) : Optional.empty();
        if (resumeMode && resumeState.isEmpty()) {
            return new WorkspacePublishReport(
                    List.of(),
                    List.of("no publish resume state found at " + WorkspacePublishPaths.displayPath(workspace, statePath)
                            + ". `--resume-members` resumes a previously interrupted `zolt publish --workspace`; "
                            + "run the full publish instead: `zolt publish --workspace`."),
                    List.of(),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }

        List<WorkspaceMember> publishable =
                WorkspacePublishSelection.publishable(workspace, selection, publishSettingsReader);
        Set<String> publishSet = new LinkedHashSet<>();
        for (WorkspaceMember member : publishable) {
            publishSet.add(member.config().project().group() + ":" + member.config().project().name());
        }

        List<MemberPublication> publications = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (WorkspaceMember member : publishable) {
            MemberPlanResult result =
                    buildMemberPlan(member, workspace, aggregatedLock, publishSet, options, resumeState, sbomGenerator);
            publications.add(result.publication());
            blockers.addAll(result.blockers());
            notes.addAll(result.notes());
        }
        if (!options.allowMixedVersions()) {
            blockers.addAll(WorkspacePublishSelection.uniformVersionBlockers(publishable));
        }
        if (options.central()) {
            blockers.addAll(WorkspaceCentralSettingsDivergence.blockers(publications));
        }

        // Phase-1 materialization of the plain-repository upload set before the first request: validate
        // every target (URL policy, credentials) and signing, and stage every checksum and signature —
        // only for a clean, live plain publish (Central assembles its own bundle; a dry run uploads none).
        List<StagedMember> stagedMembers = List.of();
        if (blockers.isEmpty() && !options.central() && !options.dryRun()) {
            WorkspacePublishStaging.Preparation preparation =
                    staging.materialize(publications, WorkspacePublishPaths.stagingRoot(workspace), options);
            blockers.addAll(preparation.blockers());
            stagedMembers = preparation.members();
            // A resume is only honoured against state whose recorded plan still matches what would upload;
            // a changed plan (or a mismatched selection/options) refuses rather than upload stale bytes.
            if (blockers.isEmpty() && resumeState.isPresent()) {
                blockers.addAll(resumeState.orElseThrow().validate(stagedMembers, options, selectionRequest.members()));
            }
        }

        List<WorkspacePublishReport.Member> members = new ArrayList<>();
        for (MemberPublication publication : publications) {
            members.add(publication.toReportMember());
        }

        if (!blockers.isEmpty()) {
            return new WorkspacePublishReport(
                    members, blockers, notes, false, Optional.empty(), Optional.empty(), Optional.empty());
        }
        if (options.central()) {
            // One atomic family bundle, one deployment id — never per-member Central deployments.
            return centralPublisher.publish(workspace, publications, options).withNotes(notes);
        }
        if (options.dryRun()) {
            return new WorkspacePublishReport(
                    members, blockers, notes, false, Optional.empty(), Optional.empty(), Optional.empty());
        }
        Set<String> completed = resumeState.map(ResumeState::completed).orElse(Set.of());
        return uploader.upload(stagedMembers, options, completed, statePath).withNotes(notes);
    }

    private MemberPlanResult buildMemberPlan(
            WorkspaceMember member,
            Workspace workspace,
            ZoltLockfile aggregatedLock,
            Set<String> publishSet,
            Options options,
            Optional<ResumeState> resumeState,
            WorkspaceMemberSbomGenerator sbomGenerator) {
        ProjectConfig config = policyResolver.merge(workspace, member);
        boolean bom = config.packageSettings().mode() == PackageMode.BOM;
        ZoltLockfile memberLock =
                bom ? bomFamily.familyLock(workspace, aggregatedLock, member) : projection.project(config, aggregatedLock);
        PublishSettings publish =
                publishSettingsReader.read(member.directory().resolve("zolt.toml"), config.repositoryCredentials());
        // Resolve the member's REAL primary artifact through the framework-aware package planner (the
        // same path single-project publishing plans) — a Quarkus fast-jar's quarkus-run.jar or any
        // future mode's real archive, not a synthesized <name>-<version>.jar. The lock only feeds the
        // planner's discarded dependency listing; the archive path derives from the member dir + config.
        Path artifactPath = bom
                ? null
                : packagePlanService.plan(member.directory(), config, workspace.root().resolve("zolt.lock"))
                        .archivePath().toAbsolutePath().normalize();
        // The POM plan below consumes the POM-shaped memberLock; the SBOM consumes the full closure.
        Optional<Path> sbomFile = bom
                ? Optional.empty()
                : sbomGenerator.generate(member.directory(), config,
                        sbomProjection.project(config, aggregatedLock, workspace, policyResolver));

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
        List<String> notes = new ArrayList<>();
        if (!bom) {
            for (String sibling : PublishInterMemberGuard.missingSiblings(memberLock, publishSet)) {
                if (resumeState.isPresent()) {
                    // A resumed set omits providers only when the state proves they landed; an absent
                    // sibling the state does not record as published is an incomplete family, not a note.
                    if (resumeState.orElseThrow().recordsPublished(sibling)) {
                        notes.add("inter-member dependency `" + sibling + "` of `" + coordinateString(config)
                                + "` is absent from the resumed set and recorded as already published; "
                                + "treating it as satisfied.");
                    } else {
                        extraBlockers.add("inter-member dependency `" + sibling + "` of `" + coordinateString(config)
                                + "` was never uploaded in the interrupted publish; re-run the full publish: "
                                + "`zolt publish --workspace`.");
                    }
                } else {
                    extraBlockers.add("inter-member dependency `" + sibling + "` of `" + coordinateString(config)
                            + "` is not in the publish set; publish the family together or add `--member` for it.");
                }
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
        return new MemberPlanResult(publication, finalPlan.blockers(), notes);
    }

    private static String coordinateString(ProjectConfig config) {
        return config.project().group() + ":" + config.project().name() + ":" + config.project().version();
    }

    private record MemberPlanResult(MemberPublication publication, List<String> blockers, List<String> notes) {
    }

    /** Publish options mirroring the CLI flags. */
    public record Options(
            boolean dryRun, boolean central, boolean allowMixedVersions, boolean sbom, Optional<Duration> waitTimeout) {
        public Options {
            waitTimeout = waitTimeout == null ? Optional.empty() : waitTimeout;
        }

        public Options(boolean dryRun, boolean central, boolean allowMixedVersions, Optional<Duration> waitTimeout) {
            this(dryRun, central, allowMixedVersions, false, waitTimeout);
        }

        /**
         * Renders the exact resume command re-running only {@code members}, preserving the family-scoped
         * semantic options so a resume never silently drops them. Members are selected exactly (no
         * dependency expansion) through {@code --resume-members}. Render every family-scoped option from
         * this record here so a newly added one is structurally carried into the resume command.
         */
        String resumeCommand(List<String> members) {
            StringBuilder command = new StringBuilder("zolt publish --workspace");
            command.append(" --resume-members ").append(String.join(",", members));
            if (allowMixedVersions) {
                command.append(" --allow-mixed-versions");
            }
            if (sbom) {
                command.append(" --sbom");
            }
            return command.toString();
        }
    }
}
