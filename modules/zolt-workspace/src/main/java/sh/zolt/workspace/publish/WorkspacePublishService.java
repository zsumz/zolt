package sh.zolt.workspace.publish;

import sh.zolt.build.packaging.PackageArtifactPathPlanner;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryPathBuilder;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionPolicy;
import sh.zolt.publish.PublishCentralReadinessService;
import sh.zolt.publish.PublishCentralRequirement;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishInterMemberGuard;
import sh.zolt.publish.PublishPomGenerator;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSettingsReader;
import sh.zolt.workspace.resolve.WorkspaceMemberPolicyResolver;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceBuildPlan;
import sh.zolt.workspace.service.WorkspaceBuildService;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceSelection;
import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * from config, versions from the aggregated lock), generates and validates the POM, checks Central
 * readiness, and verifies inter-member completeness. Every blocker across every member is aggregated
 * into one family report; any blocker means nothing uploads.
 *
 * <p><strong>Phase 2.</strong> A plain repository receives a dependency-ordered sequential upload
 * (provider before consumer, BOM last) that fails fast with an exact resume command. (Central family
 * bundling is delegated to the publish layer.) The default enforces a uniform family version;
 * {@code allowMixedVersions} opts out.
 */
public final class WorkspacePublishService {
    private final WorkspaceBuildService workspaceBuildService;
    private final WorkspaceMemberPolicyResolver policyResolver;
    private final WorkspaceMemberPomLockProjection projection;
    private final WorkspaceBomFamily bomFamily;
    private final PublishPomGenerator pomGenerator;
    private final PublishSettingsReader publishSettingsReader;
    private final PublishCentralReadinessService centralReadinessService;
    private final PackageArtifactPathPlanner artifactPathPlanner;
    private final MavenRepositoryPathBuilder repositoryPathBuilder;
    private final WorkspaceRepositoryUploader uploader;
    private final WorkspaceCentralPublisher centralPublisher;

    public WorkspacePublishService() {
        this(
                new WorkspaceBuildService(),
                new WorkspaceMemberPolicyResolver(),
                new WorkspaceMemberPomLockProjection(),
                new WorkspaceBomFamily(),
                new PublishPomGenerator(),
                new PublishSettingsReader(),
                new PublishCentralReadinessService(),
                new PackageArtifactPathPlanner(),
                new MavenRepositoryPathBuilder(),
                new WorkspaceRepositoryUploader(),
                new WorkspaceCentralPublisher());
    }

    WorkspacePublishService(
            WorkspaceBuildService workspaceBuildService,
            WorkspaceMemberPolicyResolver policyResolver,
            WorkspaceMemberPomLockProjection projection,
            WorkspaceBomFamily bomFamily,
            PublishPomGenerator pomGenerator,
            PublishSettingsReader publishSettingsReader,
            PublishCentralReadinessService centralReadinessService,
            PackageArtifactPathPlanner artifactPathPlanner,
            MavenRepositoryPathBuilder repositoryPathBuilder,
            WorkspaceRepositoryUploader uploader,
            WorkspaceCentralPublisher centralPublisher) {
        this.workspaceBuildService = workspaceBuildService;
        this.policyResolver = policyResolver;
        this.projection = projection;
        this.bomFamily = bomFamily;
        this.pomGenerator = pomGenerator;
        this.publishSettingsReader = publishSettingsReader;
        this.centralReadinessService = centralReadinessService;
        this.artifactPathPlanner = artifactPathPlanner;
        this.repositoryPathBuilder = repositoryPathBuilder;
        this.uploader = uploader;
        this.centralPublisher = centralPublisher;
    }

    public WorkspacePublishReport publish(
            Path startDirectory, Path cacheRoot, WorkspaceSelectionRequest selectionRequest, Options options) {
        WorkspaceBuildPlan plan = workspaceBuildService.planBuild(startDirectory, cacheRoot, false, selectionRequest);
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
        ZoltLockfile aggregatedLock = plan.lockfile();

        List<WorkspaceMember> publishable = publishableMembers(workspace, selection);
        Set<String> publishSet = new LinkedHashSet<>();
        for (WorkspaceMember member : publishable) {
            publishSet.add(member.config().project().group() + ":" + member.config().project().name());
        }

        List<WorkspacePublishReport.Member> members = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        for (WorkspaceMember member : publishable) {
            MemberPlanResult result = buildMemberPlan(member, workspace, aggregatedLock, publishSet, options);
            members.add(new WorkspacePublishReport.Member(
                    member.path(), coordinateString(member.config()), result.bom(), result.plan()));
            blockers.addAll(result.blockers());
        }
        if (!options.allowMixedVersions()) {
            blockers.addAll(uniformVersionBlockers(publishable));
        }

        if (!blockers.isEmpty()) {
            return new WorkspacePublishReport(members, blockers, false, Optional.empty(), Optional.empty());
        }
        if (options.central()) {
            // One atomic family bundle, one deployment id — never per-member Central deployments.
            return centralPublisher.publish(workspace, members, options);
        }
        if (options.dryRun()) {
            return new WorkspacePublishReport(members, blockers, false, Optional.empty(), Optional.empty());
        }
        return uploader.upload(workspace, members, options);
    }

    private MemberPlanResult buildMemberPlan(
            WorkspaceMember member,
            Workspace workspace,
            ZoltLockfile aggregatedLock,
            Set<String> publishSet,
            Options options) {
        ProjectConfig config = policyResolver.merge(workspace, member);
        boolean bom = config.packageSettings().mode() == PackageMode.BOM;
        ZoltLockfile memberLock =
                bom ? bomFamily.familyLock(workspace, aggregatedLock, member) : projection.project(config, aggregatedLock);

        String artifactBase = config.project().name() + "-" + config.project().version();
        Path publishDirectory = member.directory().resolve(config.build().outputRoot()).resolve("publish");
        Path pomPath = publishDirectory.resolve(artifactBase + ".pom");
        String pomSha256;
        try {
            Files.createDirectories(publishDirectory);
            Files.writeString(pomPath, pomGenerator.generate(config, memberLock));
            pomSha256 = "sha256:" + Sha256.hex(pomPath);
        } catch (IOException exception) {
            throw new sh.zolt.workspace.WorkspaceConfigException(
                    "Could not write POM for " + member.path() + ": " + exception.getMessage());
        }

        Coordinate coordinate = new Coordinate(
                config.project().group(), config.project().name(), Optional.of(config.project().version()));
        String versionKind = VersionPolicy.classifyPublishVersion(config.project().version());
        PublishSettings publish =
                publishSettingsReader.read(member.directory().resolve("zolt.toml"), config.repositoryCredentials());
        String repositoryId = versionKind.equals("snapshot") ? publish.snapshotRepository() : publish.releaseRepository();
        PublishRepositorySettings repository = publish.repositories().get(repositoryId);

        List<String> blockers = new ArrayList<>();
        Path artifactRelative = pomPath;
        String artifactSha256 = pomSha256;
        String artifactUploadPath = "";
        if (!bom) {
            Path jarPath = artifactPathPlanner.jarPath(member.directory(), config);
            artifactUploadPath = repositoryPathBuilder.jarPath(coordinate);
            if (!Files.isRegularFile(jarPath)) {
                blockers.add("missing artifact for " + coordinateString(config)
                        + ": run `zolt package --workspace` before publishing.");
                artifactRelative = jarPath;
            } else {
                artifactRelative = jarPath;
                try {
                    artifactSha256 = "sha256:" + Sha256.hex(jarPath);
                } catch (IOException exception) {
                    blockers.add("could not checksum artifact for " + coordinateString(config));
                }
            }
            for (String sibling : PublishInterMemberGuard.missingSiblings(memberLock, publishSet)) {
                blockers.add("inter-member dependency `" + sibling + "` of `" + coordinateString(config)
                        + "` is not in the publish set; publish the family together or add `--member` for it.");
            }
        }

        PublishDryRunPlan memberPlan = new PublishDryRunPlan(
                coordinateString(config),
                versionKind,
                repository != null ? repository.id() : "maven-central",
                repository != null ? repository.url() : "",
                bom ? "bom" : "main",
                member.directory().relativize(artifactRelative),
                artifactSha256,
                artifactUploadPath,
                List.of(),
                member.directory().relativize(pomPath),
                member.directory().relativize(pomPath),
                pomSha256,
                repositoryPathBuilder.pomPath(coordinate),
                List.of(),
                "",
                blockers,
                bom);

        List<String> allBlockers = new ArrayList<>(blockers);
        if (options.central()) {
            List<String> centralBlockers = new ArrayList<>();
            for (PublishCentralRequirement requirement :
                    centralReadinessService.evaluate(member.directory(), memberPlan)) {
                if (!requirement.satisfied()) {
                    centralBlockers.add(
                            coordinateString(config) + ": " + requirement.name() + " — " + requirement.remediation());
                }
            }
            allBlockers.addAll(centralBlockers);
            memberPlan = memberPlan.withContext("", centralBlockers);
        }
        return new MemberPlanResult(bom, memberPlan, allBlockers);
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

    private record MemberPlanResult(boolean bom, PublishDryRunPlan plan, List<String> blockers) {
    }

    /** Publish options mirroring the CLI flags. */
    public record Options(boolean dryRun, boolean central, boolean allowMixedVersions, boolean sbom) {
    }
}
