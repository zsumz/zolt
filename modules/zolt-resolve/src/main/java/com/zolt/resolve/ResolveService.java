package com.zolt.resolve;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.toml.ZoltLockfileWriter;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.repository.MavenRepositoryClient;
import com.zolt.maven.repository.RawPomParser;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.framework.FrameworkDependencyRequestPlanRequestAssembler;
import com.zolt.resolve.framework.FrameworkDependencyRequestPlanner;
import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.lockfile.assembly.LockfileAssembler;
import com.zolt.resolve.lockfile.persistence.ResolveLockfilePersistence;
import com.zolt.resolve.materialization.session.RepositorySession;
import com.zolt.resolve.metrics.ResolveMetrics;
import com.zolt.resolve.metadata.DependencyMetadataSource;
import com.zolt.resolve.metadata.platform.ManagedVersion;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.request.DependencyRequestPlanner;
import com.zolt.resolve.traversal.DependencyGraphTraverser;
import com.zolt.resolve.traversal.DependencyRelocator;
import com.zolt.resolve.version.VersionConflict;
import com.zolt.resolve.version.VersionSelectionResult;
import com.zolt.resolve.version.VersionSelector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResolveService {
    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final DependencyGraphResolver graphResolver;
    private final DependencyRequestPlanner dependencyRequestPlanner;
    private final LockfileAssembler lockfileAssembler;
    private final ResolveLockfilePersistence lockfilePersistence;
    private final FrameworkDependencyRequestPlanRequestAssembler frameworkPlanRequestAssembler =
            new FrameworkDependencyRequestPlanRequestAssembler();
    private final FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner;

    public ResolveService() {
        this(FrameworkDependencyRequestPlanner.none());
    }

    public ResolveService(FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this(new CoordinateParser(), frameworkDependencyRequestPlanner);
    }

    private ResolveService(
            CoordinateParser coordinateParser,
            FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this(
                coordinateParser,
                new MavenRepositoryClient(),
                new RawPomParser(),
                DependencyGraphTraverser::new,
                new VersionSelector(),
                new ZoltLockfileWriter(),
                defaultDependencyRequestPlanner(coordinateParser),
                new LockfileAssembler(coordinateParser),
                frameworkDependencyRequestPlanner);
    }

    ResolveService(
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser,
            DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector,
            ZoltLockfileWriter lockfileWriter,
            DependencyRequestPlanner dependencyRequestPlanner,
            LockfileAssembler lockfileAssembler,
            FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.graphResolver = new DependencyGraphResolver(graphTraverserFactory, versionSelector);
        this.dependencyRequestPlanner = dependencyRequestPlanner == null
                ? defaultDependencyRequestPlanner(coordinateParser)
                : dependencyRequestPlanner;
        this.lockfileAssembler = lockfileAssembler == null
                ? new LockfileAssembler(coordinateParser)
                : lockfileAssembler;
        this.lockfilePersistence = new ResolveLockfilePersistence(
                lockfileWriter == null ? new ZoltLockfileWriter() : lockfileWriter);
        this.frameworkDependencyRequestPlanner = frameworkDependencyRequestPlanner == null
                ? FrameworkDependencyRequestPlanner.none()
                : frameworkDependencyRequestPlanner;
    }

    private static DependencyRequestPlanner defaultDependencyRequestPlanner(CoordinateParser coordinateParser) {
        return new DependencyRequestPlanner(coordinateParser);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked) {
        return resolve(projectDirectory, config, cacheRoot, locked, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked, boolean offline) {
        return resolve(projectDirectory, config, cacheRoot, locked, ResolveOptions.offline(offline));
    }

    public ResolveResult resolve(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean locked,
            ResolveOptions options) {
        Path lockfilePath = lockfilePersistence.lockfilePath(projectDirectory);
        options = lockfilePersistence.prepare(lockfilePath, locked, options);

        ResolveOutput output = resolveLockfile(config, cacheRoot, options);
        ZoltLockfile lockfile = output.lockfile();
        ResolveMetrics metrics = lockfilePersistence.persist(
                lockfilePath,
                lockfile,
                output.metrics(),
                locked,
                options);
        return new ResolveResult(
                lockfile.packages().size(),
                output.downloadCount(),
                lockfile.conflicts().size(),
                lockfilePath,
                metrics);
    }

    public ResolveResult resolveWithCoverageTooling(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false, ResolveOptions.defaults().withCoverageTooling());
    }

    public ResolveOutput resolveLockfile(ProjectConfig config, Path cacheRoot, boolean offline) {
        return resolveLockfile(config, cacheRoot, ResolveOptions.offline(offline));
    }

    public ResolveOutput resolveLockfile(ProjectConfig config, Path cacheRoot, ResolveOptions options) {
        RepositorySession context =
                new RepositorySession(config, cacheRoot, options, coordinateParser, repositoryClient, rawPomParser);
        Map<PackageId, ManagedVersion> managedVersionDetails = context.projectManagedVersionDetails();
        Map<PackageId, String> managedVersions = context.projectManagedVersions();
        List<DependencyRequest> directRequests = dependencyRequestPlanner.plan(
                config,
                managedVersions,
                options.includeCoverageTooling(),
                options.retryCommand());
        directRequests = relocateDirectRequests(context, directRequests);
        DependencyGraphResolution initial = graphResolver.resolve(
                context,
                context.config().dependencyPolicy(),
                managedVersionDetails,
                directRequests,
                context,
                options.retryCommand());
        List<DependencyRequest> allRequests = new ArrayList<>(directRequests);
        allRequests.addAll(frameworkDependencyRequestPlanner.plan(frameworkPlanRequestAssembler.assemble(
                context.config(),
                initial.graph(),
                initial.selection(),
                directRequests,
                managedVersions,
                coordinate -> context.getJar(coordinate).cachePath(),
                context::projectPlatformPropertiesRequests)));
        DependencyGraphResolution resolved = allRequests.size() == directRequests.size()
                ? initial
                : graphResolver.resolve(
                        context,
                        context.config().dependencyPolicy(),
                        managedVersionDetails,
                        allRequests,
                        context,
                        options.retryCommand());
        enforceVersionConflictPolicy(context.config().dependencyPolicy(), resolved.selection(), options.retryCommand());
        ZoltLockfile lockfile = lockfile(context, resolved.graph(), resolved.selection(), allRequests);
        return new ResolveOutput(lockfile, context.downloadCount(), context.metrics());
    }

    private List<DependencyRequest> relocateDirectRequests(
            RepositorySession context,
            List<DependencyRequest> directRequests) {
        DependencyRelocator relocator = new DependencyRelocator(context);
        return directRequests.stream()
                .map(relocator::relocate)
                .toList();
    }

    private ZoltLockfile lockfile(
            RepositorySession context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        return lockfileAssembler.assemble(context, graph, selection, directRequests);
    }

    private static void enforceVersionConflictPolicy(
            DependencyPolicySettings dependencyPolicy,
            VersionSelectionResult selection,
            String retryCommand) {
        if (dependencyPolicy == null
                || !dependencyPolicy.failOnVersionConflict()
                || selection.conflicts().isEmpty()) {
            return;
        }
        List<String> conflicts = selection.conflicts().stream()
                .sorted(Comparator.comparing(conflict -> conflict.packageId().toString()))
                .map(ResolveService::conflictDescription)
                .toList();
        throw ResolveException.actionable(
                "Dependency version conflicts are disallowed by [dependencyPolicy].failOnVersionConflict.",
                "Align the conflicting versions with a [platforms] BOM, a direct dependency, or a "
                        + "[dependencyConstraints] strict constraint, then run `"
                        + retryCommand
                        + "` again. Conflicts: "
                        + String.join("; ", conflicts));
    }

    private static String conflictDescription(VersionConflict conflict) {
        return conflict.packageId()
                + " selected "
                + conflict.selectedVersion()
                + " ("
                + reason(conflict.selectionReason())
                + "), requested "
                + requestedVersions(conflict);
    }

    private static String requestedVersions(VersionConflict conflict) {
        return String.join(", ", conflict.requests().stream()
                .map(request -> request.requestedVersion()
                        + " ["
                        + request.origin().name().toLowerCase(Locale.ROOT)
                        + " "
                        + request.scope().lockfileName()
                        + "]")
                .distinct()
                .sorted()
                .toList());
    }

    private static String reason(ConflictSelectionReason reason) {
        return switch (reason) {
            case DIRECT_DEPENDENCY -> "direct dependency wins";
            case NEWEST_VERSION -> "newest version wins";
        };
    }

    @FunctionalInterface
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(
                DependencyMetadataSource source,
                DependencyPolicySettings dependencyPolicy,
                Map<PackageId, ManagedVersion> managedVersions,
                String retryCommand);
    }

}
