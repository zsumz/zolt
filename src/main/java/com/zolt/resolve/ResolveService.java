package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.LockfileFreshnessSummary;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.MavenRepositoryClient;
import com.zolt.maven.MavenRepositoryPathBuilder;
import com.zolt.maven.PomInterpolationException;
import com.zolt.maven.PomPropertyInterpolator;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.maven.RawPomParser;
import com.zolt.maven.RepositoryAuthentication;
import com.zolt.maven.RepositoryMissingArtifactException;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ResolveService {
    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final ZoltLockfileWriter lockfileWriter;
    private final DependencyGraphResolver graphResolver;
    private final DependencyRequestPlanner dependencyRequestPlanner;
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
            FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.lockfileWriter = lockfileWriter;
        this.graphResolver = new DependencyGraphResolver(graphTraverserFactory, versionSelector);
        this.dependencyRequestPlanner = dependencyRequestPlanner == null
                ? defaultDependencyRequestPlanner(coordinateParser)
                : dependencyRequestPlanner;
        this.frameworkDependencyRequestPlanner = frameworkDependencyRequestPlanner == null
                ? FrameworkDependencyRequestPlanner.none()
                : frameworkDependencyRequestPlanner;
    }

    private static DependencyRequestPlanner defaultDependencyRequestPlanner(CoordinateParser coordinateParser) {
        return new DependencyRequestPlanner(
                coordinateParser,
                new ToolingDependencyContributor(coordinateParser));
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
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve` to create it, then retry `zolt resolve --locked`.");
        }
        if (locked && options.rejectLocalOverlays()) {
            rejectExistingLocalOverlayLockfile(lockfilePath);
        }
        if (!options.includeCoverageTooling() && existingLockfileHasCoverageTooling(lockfilePath)) {
            options = options.withCoverageTooling();
        }

        ResolveOutput output = resolveLockfile(config, cacheRoot, options);
        ZoltLockfile lockfile = output.lockfile();
        if (options.rejectLocalOverlays()) {
            rejectLocalOverlayLockfile(lockfile);
        }
        ResolveMetrics metrics = output.metrics();
        if (locked) {
            long started = System.nanoTime();
            verifyLocked(lockfilePath, lockfile);
            metrics = metrics.withLockfileVerificationNanos(elapsedSince(started));
        } else {
            long started = System.nanoTime();
            lockfileWriter.write(lockfilePath, lockfile);
            metrics = metrics.withLockfileWriteNanos(elapsedSince(started));
        }
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
        RepositoryContext context = new RepositoryContext(config, new LocalArtifactCache(cacheRoot), options);
        Map<PackageId, String> managedVersions = context.projectManagedVersions();
        List<DependencyRequest> directRequests = dependencyRequestPlanner.plan(
                config,
                managedVersions,
                options.includeCoverageTooling());
        directRequests = relocateDirectRequests(context, directRequests);
        DependencyGraphResolution initial = graphResolver.resolve(
                context,
                context.config.dependencyPolicy(),
                directRequests,
                context);
        List<DependencyRequest> allRequests = new ArrayList<>(directRequests);
        allRequests.addAll(frameworkDependencyRequestPlanner.plan(frameworkDependencyRequestPlanRequest(
                context,
                initial.graph(),
                initial.selection(),
                directRequests,
                managedVersions)));
        DependencyGraphResolution resolved = allRequests.size() == directRequests.size()
                ? initial
                : graphResolver.resolve(
                        context,
                        context.config.dependencyPolicy(),
                        allRequests,
                        context);
        ZoltLockfile lockfile = lockfile(context, resolved.graph(), resolved.selection(), allRequests);
        return new ResolveOutput(lockfile, context.downloadCount(), context.metrics());
    }

    private void rejectExistingLocalOverlayLockfile(Path lockfilePath) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " while checking local overlay origins. Check that the file exists and is readable.",
                    exception);
        }
        if (existing.contains("source = \"local-overlay:")) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private void rejectLocalOverlayLockfile(ZoltLockfile lockfile) {
        boolean hasLocalOverlay = lockfile.packages().stream()
                .anyMatch(lockPackage -> localOverlaySource(lockPackage.source()));
        if (hasLocalOverlay) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private static boolean existingLockfileHasCoverageTooling(Path lockfilePath) {
        if (!Files.isRegularFile(lockfilePath)) {
            return false;
        }
        try {
            return new ZoltLockfileReader().read(lockfilePath).packages().stream()
                    .anyMatch(lockPackage -> lockPackage.scope() == DependencyScope.TOOL_COVERAGE);
        } catch (LockfileReadException exception) {
            return false;
        }
    }

    private static boolean localOverlaySource(String source) {
        return source != null && source.startsWith("local-overlay:");
    }

    private static String localOverlayRejectedMessage() {
        return "Local repository overlay artifacts are not allowed for this resolve. "
                + "Run `zolt resolve` without local overlays to refresh zolt.lock from configured repositories, "
                + "or remove --no-local-overlays for a local development-only resolve.";
    }

    private List<DependencyRequest> relocateDirectRequests(
            RepositoryContext context,
            List<DependencyRequest> directRequests) {
        DependencyRelocator relocator = new DependencyRelocator(context);
        return directRequests.stream()
                .map(relocator::relocate)
                .toList();
    }

    private void verifyLocked(Path lockfilePath, ZoltLockfile candidate) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " for locked resolve. Check that the file exists and is readable.",
                    exception);
        }

        String expected = lockfileWriter.write(candidate);
        if (!existing.equals(expected)) {
            String changedInputs = changedInputs(existing, candidate);
            throw new ResolveException(
                    "zolt.lock is out of date."
                            + changedInputs
                            + " Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.");
        }
    }

    private static String changedInputs(String existing, ZoltLockfile candidate) {
        try {
            return LockfileFreshnessSummary.changedInputs(new ZoltLockfileReader().read(existing), candidate);
        } catch (LockfileReadException exception) {
            return "";
        }
    }

    private static List<GeneratedSourceStep> openApiSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        return List.copyOf(steps);
    }

    private static boolean isSpringBootArchive(PackageMode mode) {
        return mode == PackageMode.SPRING_BOOT || mode == PackageMode.SPRING_BOOT_WAR;
    }

    private ZoltLockfile lockfile(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        long started = System.nanoTime();
        try {
            Map<PackageId, List<SelectedDependencyScope>> selectedScopes = SelectedDependencyScopes.from(
                    graph,
                    selection,
                    directRequests);
            List<LockPackagePlan> packagePlans = selection.selectedNodes().stream()
                    .flatMap(node -> selectedScopes
                            .getOrDefault(node.packageId(), List.of(new SelectedDependencyScope(DependencyScope.COMPILE, false)))
                            .stream()
                            .map(scope -> lockPackagePlan(node, scope)))
                    .toList();
            Map<ArtifactDescriptor, CachedArtifact> artifacts = context.getArtifacts(
                    packagePlans.stream().map(LockPackagePlan::artifactDescriptor).toList());
            Map<PackageId, List<DependencyScope>> managedDirectScopes = managedDirectScopes(context.config);
            Map<PackageId, ManagedVersion> managedVersionDetails = context.projectManagedVersionDetails();
            List<LockPackage> packages = packagePlans.stream()
                    .map(plan -> lockPackage(
                            context,
                            plan,
                            graph,
                            artifacts.get(plan.artifactDescriptor()),
                            managedDirectScopes,
                            managedVersionDetails,
                            context.config.dependencyMetadata(),
                            graph.policyEffects()))
                    .toList();
            List<LockConflict> conflicts = selection.conflicts().stream()
                    .map(conflict -> new LockConflict(
                            conflict.packageId(),
                            conflict.selectedVersion(),
                            conflict.requests().stream().map(DependencyRequest::requestedVersion).toList(),
                            conflict.selectionReason()))
                    .toList();
            return new ZoltLockfile(
                    ZoltLockfile.CURRENT_VERSION,
                    aliasFingerprint(context.config),
                    Optional.of(ProjectResolutionFingerprint.fingerprint(context.config)),
                    ProjectResolutionFingerprint.inputFingerprints(context.config),
                    packages,
                    conflicts,
                    lockPolicyEffects(graph.policyEffects()));
        } finally {
            context.addLockfileAssemblyNanos(elapsedSince(started));
        }
    }

    private static Optional<String> aliasFingerprint(ProjectConfig config) {
        List<String> inputs = new ArrayList<>();
        config.versionAliases().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "alias\t" + entry.getKey() + "\t" + entry.getValue())
                .forEach(inputs::add);
        config.dependencyMetadata().values().stream()
                .filter(metadata -> metadata.versionRef() != null)
                .sorted(Comparator
                        .comparing(DependencyMetadata::section)
                        .thenComparing(DependencyMetadata::coordinate))
                .map(metadata -> "versionRef\t"
                        + metadata.section()
                        + "\t"
                        + metadata.coordinate()
                        + "\t"
                        + metadata.versionRef()
                        + "\t"
                        + nullToEmpty(metadata.version()))
                .forEach(inputs::add);
        config.dependencyPolicy().constraints().values().stream()
                .filter(constraint -> constraint.versionRef().isPresent())
                .sorted(Comparator.comparing(DependencyConstraint::coordinate))
                .map(constraint -> "constraintVersionRef\t"
                        + constraint.coordinate()
                        + "\t"
                        + constraint.versionRef().orElseThrow()
                        + "\t"
                        + constraint.version())
                .forEach(inputs::add);
        openApiSteps(config).stream()
                .map(GeneratedSourceStep::openApi)
                .filter(settings -> settings.toolVersionRef().isPresent())
                .sorted(Comparator
                        .comparing((OpenApiGenerationSettings settings) -> settings.toolCoordinate().orElse(""))
                        .thenComparing(settings -> settings.toolVersionRef().orElseThrow()))
                .map(settings -> "openApiToolVersionRef\t"
                        + settings.toolCoordinate().orElse("")
                        + "\t"
                        + settings.toolVersionRef().orElseThrow()
                        + "\t"
                        + settings.toolVersion().orElse(""))
                .forEach(inputs::add);
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        String input = String.join("\n", inputs) + "\n";
        return Optional.of("sha256:" + sha256(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private FrameworkDependencyRequestPlanRequest frameworkDependencyRequestPlanRequest(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests,
            Map<PackageId, String> managedVersions) {
        Map<PackageId, List<SelectedDependencyScope>> selectedScopes = SelectedDependencyScopes.from(
                graph,
                selection,
                directRequests);
        List<FrameworkDependencyCandidate> candidates = selection.selectedNodes().stream()
                .sorted(Comparator.comparing(node -> node.packageId() + ":" + node.selectedVersion()))
                .map(node -> new FrameworkDependencyCandidate(
                        node.packageId(),
                        node.selectedVersion(),
                        selectedScopes.getOrDefault(node.packageId(), List.of()).stream()
                                .map(SelectedDependencyScope::scope)
                                .toList()))
                .toList();
        Map<PackageId, String> versions = new LinkedHashMap<>();
        for (PackageNode node : selection.selectedNodes()) {
            versions.put(node.packageId(), node.selectedVersion());
        }
        return new FrameworkDependencyRequestPlanRequest(
                context.config,
                candidates,
                versions,
                managedVersions,
                coordinate -> context.getJar(coordinate).cachePath(),
                context::projectPlatformPropertiesRequests);
    }

    private static LockPackagePlan lockPackagePlan(PackageNode node, SelectedDependencyScope selectedScope) {
        Coordinate coordinate = new Coordinate(
                node.packageId().groupId(),
                node.packageId().artifactId(),
                Optional.of(node.selectedVersion()));
        ArtifactDescriptor descriptor = selectedScope.artifactDescriptor()
                .orElseGet(() -> ArtifactDescriptor.jar(coordinate));
        return new LockPackagePlan(node, selectedScope, descriptor);
    }

    private LockPackage lockPackage(
            RepositoryContext context,
            LockPackagePlan plan,
            ResolutionGraph graph,
            CachedArtifact artifact,
            Map<PackageId, List<DependencyScope>> managedDirectScopes,
            Map<PackageId, ManagedVersion> managedVersionDetails,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<DependencyPolicyEffect> policyEffects) {
        PackageNode node = plan.node();
        SelectedDependencyScope selectedScope = plan.selectedScope();
        ArtifactDescriptor descriptor = plan.artifactDescriptor();
        CachedArtifact pom = context.getPom(descriptor.coordinate());
        boolean jarArtifact = "jar".equals(descriptor.extension());
        return new LockPackage(
                node.packageId(),
                node.selectedVersion(),
                context.sourceFor(artifact),
                selectedScope.scope(),
                selectedScope.direct(),
                jarArtifact ? Optional.of(artifact.repositoryPath()) : Optional.empty(),
                Optional.of(pom.repositoryPath()),
                jarArtifact ? Optional.of(sha256(artifact.bytes())) : Optional.empty(),
                Optional.of(sha256(pom.bytes())),
                jarArtifact ? Optional.empty() : Optional.of(artifact.repositoryPath()),
                jarArtifact ? Optional.empty() : Optional.of(descriptor.extension()),
                jarArtifact ? Optional.empty() : Optional.of(sha256(artifact.bytes())),
                dependenciesFor(node, graph),
                policiesFor(
                        node,
                        selectedScope,
                        context.config.dependencyPolicy().constraints(),
                        managedDirectScopes,
                        managedVersionDetails,
                        dependencyMetadata,
                        policyEffects));
    }

    private static List<String> dependenciesFor(PackageNode node, ResolutionGraph graph) {
        return graph.edges().stream()
                .filter(edge -> edge.from().equals(node))
                .map(edge -> edge.to().packageId() + ":" + edge.to().selectedVersion())
                .sorted()
                .toList();
    }

    private static List<String> policiesFor(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            Map<String, DependencyConstraint> constraints,
            Map<PackageId, List<DependencyScope>> managedDirectScopes,
            Map<PackageId, ManagedVersion> managedVersions,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<DependencyPolicyEffect> policyEffects) {
        List<String> policies = new ArrayList<>();
        if (selectedScope.direct()) {
            policies.addAll(versionRefPolicies(node, selectedScope, dependencyMetadata));
        }
        if (selectedScope.direct()
                && managedDirectScopes.getOrDefault(node.packageId(), List.of()).contains(selectedScope.scope())) {
            ManagedVersion managedVersion = managedVersions.get(node.packageId());
            if (managedVersion != null && managedVersion.version().equals(node.selectedVersion())) {
                policies.add("managed-version: "
                        + node.packageId()
                        + " -> "
                        + managedVersion.version()
                        + " from "
                        + managedVersion.platform());
            }
        }
        if (selectedScope.direct()) {
            return policies;
        }
        DependencyConstraint constraint = constraints.get(node.packageId().toString());
        if (constraint == null || !constraint.version().equals(node.selectedVersion())) {
            return policies;
        }
        List<String> strictPolicies = policyEffects.stream()
                .filter(effect -> "strict-version".equals(effect.kind()))
                .filter(effect -> effect.packageId().equals(node.packageId()))
                .map(DependencyPolicyEffect::policy)
                .distinct()
                .sorted()
                .toList();
        if (strictPolicies.isEmpty()) {
            String policy = "strict-version: " + node.packageId() + " -> " + constraint.version();
            policies.add(constraint.reason()
                    .map(reason -> policy + " (" + reason + ")")
                    .orElse(policy));
        } else {
            policies.addAll(strictPolicies);
        }
        return List.copyOf(policies);
    }

    private static List<String> versionRefPolicies(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            Map<String, DependencyMetadata> dependencyMetadata) {
        return dependencyMetadata.values().stream()
                .filter(metadata -> metadata.versionRef() != null)
                .filter(metadata -> metadata.coordinate().equals(node.packageId().toString()))
                .filter(metadata -> node.selectedVersion().equals(metadata.version()))
                .filter(metadata -> metadataScope(metadata.section()) == selectedScope.scope())
                .map(metadata -> "version-ref: "
                        + node.packageId()
                        + " -> "
                        + node.selectedVersion()
                        + " from [versions]."
                        + metadata.versionRef())
                .distinct()
                .sorted()
                .toList();
    }

    private static DependencyScope metadataScope(String section) {
        return switch (section) {
            case "api.dependencies", "dependencies" -> DependencyScope.COMPILE;
            case "runtime.dependencies" -> DependencyScope.RUNTIME;
            case "provided.dependencies" -> DependencyScope.PROVIDED;
            case "dev.dependencies" -> DependencyScope.DEV;
            case "test.dependencies" -> DependencyScope.TEST;
            case "annotationProcessors" -> DependencyScope.PROCESSOR;
            case "test.annotationProcessors" -> DependencyScope.TEST_PROCESSOR;
            default -> null;
        };
    }

    private static List<LockPolicyEffect> lockPolicyEffects(List<DependencyPolicyEffect> policyEffects) {
        return policyEffects.stream()
                .map(effect -> new LockPolicyEffect(
                        effect.kind(),
                        effect.packageId(),
                        effect.requestedVersion(),
                        effect.source(),
                        effect.policy()))
                .distinct()
                .sorted(Comparator.comparing(effect -> effect.kind()
                        + ":"
                        + effect.packageId()
                        + ":"
                        + effect.requestedVersion().orElse("")
                        + ":"
                        + effect.source().orElse("")
                        + ":"
                        + effect.policy()))
                .toList();
    }

    private Map<PackageId, List<DependencyScope>> managedDirectScopes(ProjectConfig config) {
        Map<PackageId, List<DependencyScope>> scopes = new LinkedHashMap<>();
        addManagedDirectScopes(scopes, config.managedApiDependencies(), DependencyScope.COMPILE);
        addManagedDirectScopes(scopes, config.managedDependencies(), DependencyScope.COMPILE);
        addManagedDirectScopes(scopes, config.managedRuntimeDependencies(), DependencyScope.RUNTIME);
        addManagedDirectScopes(scopes, config.managedProvidedDependencies(), DependencyScope.PROVIDED);
        addManagedDirectScopes(scopes, config.managedDevDependencies(), DependencyScope.DEV);
        addManagedDirectScopes(scopes, config.managedTestDependencies(), DependencyScope.TEST);
        addManagedDirectScopes(scopes, config.managedAnnotationProcessors(), DependencyScope.PROCESSOR);
        addManagedDirectScopes(scopes, config.managedTestAnnotationProcessors(), DependencyScope.TEST_PROCESSOR);
        return scopes.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .distinct()
                                .sorted(Comparator.comparing(DependencyScope::lockfileName))
                                .toList(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private void addManagedDirectScopes(
            Map<PackageId, List<DependencyScope>> scopes,
            Iterable<String> dependencies,
            DependencyScope scope) {
        for (String dependency : dependencies) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            scopes.computeIfAbsent(PackageId.from(coordinate), ignored -> new ArrayList<>()).add(scope);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResolveException("Could not compute artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    private static String artifactDescriptorKey(ArtifactDescriptor descriptor) {
        return descriptor.coordinate()
                + ":"
                + descriptor.classifier().orElse("")
                + ":"
                + descriptor.extension();
    }

    private static ResolveException artifactDownloadException(List<ArtifactDownloadFailure> failures) {
        List<ArtifactDownloadFailure> sorted = failures.stream()
                .sorted(Comparator.comparing(ArtifactDownloadFailure::artifactKey))
                .toList();
        StringBuilder message = new StringBuilder("Selected artifact downloads failed:");
        for (ArtifactDownloadFailure failure : sorted) {
            message.append(System.lineSeparator())
                    .append("- ")
                    .append(failure.artifactKey())
                    .append(": ")
                    .append(failure.message());
        }
        message.append(System.lineSeparator())
                .append("Retry the command or check your repository and network settings.");
        return new ResolveException(message.toString());
    }

    private static ResolveException pomMetadataException(List<PomMetadataFailure> failures) {
        List<PomMetadataFailure> sorted = failures.stream()
                .sorted(Comparator.comparing(PomMetadataFailure::coordinate))
                .toList();
        StringBuilder message = new StringBuilder("POM metadata fetch failed:");
        for (PomMetadataFailure failure : sorted) {
            message.append(System.lineSeparator())
                    .append("- ")
                    .append(failure.coordinate())
                    .append(": ")
                    .append(failure.message());
        }
        message.append(System.lineSeparator())
                .append("Retry the command or check your repository and network settings.");
        return new ResolveException(message.toString());
    }

    private static String failureMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return cause == null ? "download failed" : cause.getClass().getSimpleName();
        }
        return cause.getMessage();
    }

    private record LockPackagePlan(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            ArtifactDescriptor artifactDescriptor) {
    }

    private record ManagedVersion(String version, String platform) {
    }

    private record ArtifactDownloadFailure(String artifactKey, String message) {
    }

    private record PomMetadataFailure(String coordinate, String message) {
    }

    private record RepositoryAccess(
            URI uri,
            Optional<RepositoryAuthentication> authentication) {
    }

    @FunctionalInterface
    private interface RepositoryFetchAction {
        com.zolt.maven.RepositoryArtifact fetch(RepositoryAccess access);
    }

    @FunctionalInterface
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(DependencyMetadataSource source, DependencyPolicySettings dependencyPolicy);
    }

    private final class RepositoryContext implements DependencyMetadataSource, ResolverMetricsSink {
        private final ProjectConfig config;
        private final LocalArtifactCache cache;
        private final ResolveOptions options;
        private final MavenRepositoryPathBuilder repositoryPathBuilder = new MavenRepositoryPathBuilder();
        private final Map<String, EffectiveRawPom> metadata = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<EffectiveRawPom>> metadataLoads = new ConcurrentHashMap<>();
        private final Map<String, RawPom> rawPoms = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<RawPom>> rawPomLoads = new ConcurrentHashMap<>();
        private final Map<String, String> artifactSources = new ConcurrentHashMap<>();
        private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();
        private Map<PackageId, ManagedVersion> projectManagedVersions;
        private int downloadCount;
        private int pomCacheHits;
        private int pomCacheMisses;
        private int jarCacheHits;
        private int jarCacheMisses;
        private int artifactCacheHits;
        private int artifactCacheMisses;
        private int rawPomCacheHits;
        private int rawPomCacheMisses;
        private int effectivePomCacheHits;
        private int effectivePomCacheMisses;
        private long pomCacheHitNanos;
        private long pomDownloadNanos;
        private long jarCacheHitNanos;
        private long jarDownloadNanos;
        private long artifactCacheHitNanos;
        private long artifactDownloadNanos;
        private long rawPomParseNanos;
        private long effectivePomBuildNanos;
        private long graphTraversalNanos;
        private long versionSelectionNanos;
        private long lockfileAssemblyNanos;

        RepositoryContext(ProjectConfig config, LocalArtifactCache cache, ResolveOptions options) {
            this.config = config;
            this.cache = cache;
            this.options = options;
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            return effectivePom(coordinate, List.of());
        }

        @Override
        public void preload(List<Coordinate> coordinates) {
            Map<String, Coordinate> uniqueCoordinates = new LinkedHashMap<>();
            coordinates.stream()
                    .sorted(Comparator.comparing(Coordinate::toString))
                    .forEach(coordinate -> uniqueCoordinates.putIfAbsent(coordinate.toString(), coordinate));
            if (uniqueCoordinates.isEmpty()) {
                return;
            }
            try (ExecutorService executor = Executors.newFixedThreadPool(cache.downloadConcurrency())) {
                Map<String, Future<EffectiveRawPom>> futures = new LinkedHashMap<>();
                for (Map.Entry<String, Coordinate> entry : uniqueCoordinates.entrySet()) {
                    futures.put(entry.getKey(), executor.submit(() -> load(entry.getValue())));
                }

                List<PomMetadataFailure> failures = new ArrayList<>();
                for (Map.Entry<String, Future<EffectiveRawPom>> entry : futures.entrySet()) {
                    try {
                        entry.getValue().get();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failures.add(new PomMetadataFailure(
                                entry.getKey(),
                                "interrupted while fetching POM metadata"));
                    } catch (ExecutionException exception) {
                        failures.add(new PomMetadataFailure(entry.getKey(), failureMessage(exception.getCause())));
                    }
                }
                if (!failures.isEmpty()) {
                    throw pomMetadataException(failures);
                }
            }
        }

        CachedArtifact getPom(Coordinate coordinate) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayPom(coordinate);
            if (overlayArtifact.isPresent()) {
                recordPomCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact = cache.getCachedPom(coordinate);
                recordPomCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.pomPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchPom(coordinate, requested ->
                    fetchPom(requested));
            if (cached) {
                recordPomCacheHit(elapsedSince(started));
            } else {
                recordPomDownload(elapsedSince(started));
            }
            return artifact;
        }

        CachedArtifact getJar(Coordinate coordinate) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayArtifact(ArtifactDescriptor.jar(coordinate));
            if (overlayArtifact.isPresent()) {
                recordJarCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact = cache.getCachedJar(coordinate);
                recordJarCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.jarPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchJar(coordinate, requested ->
                    fetchJar(requested));
            if (cached) {
                recordJarCacheHit(elapsedSince(started));
            } else {
                recordJarDownload(elapsedSince(started));
            }
            return artifact;
        }

        CachedArtifact getArtifact(ArtifactDescriptor descriptor) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayArtifact(descriptor);
            if (overlayArtifact.isPresent()) {
                recordArtifactCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact =
                        cache.getCachedArtifact(descriptor, descriptor.extension().toUpperCase(java.util.Locale.ROOT));
                recordArtifactCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.artifactPath(descriptor);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchArtifact(descriptor, requested ->
                    fetchArtifact(descriptor));
            if (cached) {
                recordArtifactCacheHit(elapsedSince(started));
            } else {
                recordArtifactDownload(elapsedSince(started));
            }
            return artifact;
        }

        String sourceFor(CachedArtifact artifact) {
            return artifactSources.getOrDefault(artifact.repositoryPath(), "maven-central");
        }

        private Optional<CachedArtifact> materializeOverlayPom(Coordinate coordinate) {
            for (RepositoryOverlay overlay : options.repositoryOverlays()) {
                if (overlay.kind() != RepositoryOverlayKind.MAVEN_LOCAL) {
                    continue;
                }
                Path sourcePath = overlay.root().resolve(repositoryPathBuilder.pomPath(coordinate)).normalize();
                if (!Files.isRegularFile(sourcePath)) {
                    continue;
                }
                CachedArtifact artifact = cache.materializeOverlayPom(coordinate, overlay.id(), sourcePath);
                artifactSources.put(artifact.repositoryPath(), overlay.lockfileSource());
                return Optional.of(artifact);
            }
            return Optional.empty();
        }

        private Optional<CachedArtifact> materializeOverlayArtifact(ArtifactDescriptor descriptor) {
            for (RepositoryOverlay overlay : options.repositoryOverlays()) {
                if (overlay.kind() != RepositoryOverlayKind.MAVEN_LOCAL) {
                    continue;
                }
                Path sourcePath = overlay.root().resolve(repositoryPathBuilder.artifactPath(descriptor)).normalize();
                if (!Files.isRegularFile(sourcePath)) {
                    continue;
                }
                CachedArtifact artifact = cache.materializeOverlayArtifact(descriptor, overlay.id(), sourcePath);
                artifactSources.put(artifact.repositoryPath(), overlay.lockfileSource());
                return Optional.of(artifact);
            }
            return Optional.empty();
        }

        Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors) {
            Map<ArtifactDescriptor, ArtifactDescriptor> uniqueDescriptors = new LinkedHashMap<>();
            descriptors.stream()
                    .sorted(Comparator.comparing(ResolveService::artifactDescriptorKey))
                    .forEach(descriptor -> uniqueDescriptors.putIfAbsent(descriptor, descriptor));
            if (uniqueDescriptors.isEmpty()) {
                return Map.of();
            }
            try (ExecutorService executor = Executors.newFixedThreadPool(cache.downloadConcurrency())) {
                Map<ArtifactDescriptor, Future<CachedArtifact>> futures = new LinkedHashMap<>();
                for (ArtifactDescriptor descriptor : uniqueDescriptors.values()) {
                    futures.put(descriptor, executor.submit(() -> getArtifact(descriptor)));
                }

                Map<ArtifactDescriptor, CachedArtifact> artifacts = new LinkedHashMap<>();
                List<ArtifactDownloadFailure> failures = new ArrayList<>();
                for (Map.Entry<ArtifactDescriptor, Future<CachedArtifact>> entry : futures.entrySet()) {
                    try {
                        artifacts.put(entry.getKey(), entry.getValue().get());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failures.add(new ArtifactDownloadFailure(
                                artifactDescriptorKey(entry.getKey()),
                                "interrupted while materializing artifact"));
                    } catch (ExecutionException exception) {
                        failures.add(new ArtifactDownloadFailure(
                                artifactDescriptorKey(entry.getKey()),
                                failureMessage(exception.getCause())));
                    }
                }
                if (!failures.isEmpty()) {
                    throw artifactDownloadException(failures);
                }
                return artifacts;
            }
        }

        private synchronized void recordPomCacheHit(long elapsedNanos) {
            pomCacheHits++;
            pomCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordPomDownload(long elapsedNanos) {
            pomCacheMisses++;
            pomDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordJarCacheHit(long elapsedNanos) {
            jarCacheHits++;
            jarCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordJarDownload(long elapsedNanos) {
            jarCacheMisses++;
            jarDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordArtifactCacheHit(long elapsedNanos) {
            artifactCacheHits++;
            artifactCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordArtifactDownload(long elapsedNanos) {
            artifactCacheMisses++;
            artifactDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordRawPomCacheHit() {
            rawPomCacheHits++;
        }

        private synchronized void recordRawPomCacheMiss() {
            rawPomCacheMisses++;
        }

        private synchronized void recordRawPomParse(long elapsedNanos) {
            rawPomParseNanos += elapsedNanos;
        }

        private synchronized void recordEffectivePomCacheHit() {
            effectivePomCacheHits++;
        }

        private synchronized void recordEffectivePomCacheMiss() {
            effectivePomCacheMisses++;
        }

        private synchronized void recordEffectivePomBuild(long elapsedNanos) {
            effectivePomBuildNanos += elapsedNanos;
        }

        private EffectiveRawPom awaitEffectivePom(String key, CompletableFuture<EffectiveRawPom> future) {
            return awaitPomFuture("effective POM", key, future);
        }

        private RawPom awaitRawPom(String key, CompletableFuture<RawPom> future) {
            return awaitPomFuture("raw POM", key, future);
        }

        private <T> T awaitPomFuture(String kind, String key, CompletableFuture<T> future) {
            try {
                return future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResolveException(
                        "Interrupted while waiting for in-flight "
                                + kind
                                + " metadata "
                                + key
                                + ". Try again.",
                        exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new ResolveException(
                        "Could not load in-flight "
                                + kind
                                + " metadata "
                                + key
                                + ". Try again.",
                        cause);
            }
        }

        int downloadCount() {
            return downloadCount;
        }

        synchronized ResolveMetrics metrics() {
            return new ResolveMetrics(
                    pomCacheHits,
                    pomCacheMisses,
                    jarCacheHits,
                    jarCacheMisses,
                    artifactCacheHits,
                    artifactCacheMisses,
                    rawPomCacheHits,
                    rawPomCacheMisses,
                    effectivePomCacheHits,
                    effectivePomCacheMisses,
                    pomCacheHitNanos,
                    pomDownloadNanos,
                    jarCacheHitNanos,
                    jarDownloadNanos,
                    artifactCacheHitNanos,
                    artifactDownloadNanos,
                    rawPomParseNanos,
                    effectivePomBuildNanos,
                    graphTraversalNanos,
                    versionSelectionNanos,
                    lockfileAssemblyNanos,
                    0L,
                    0L);
        }

        @Override
        public void addGraphTraversalNanos(long nanos) {
            graphTraversalNanos += nanos;
        }

        @Override
        public void addVersionSelectionNanos(long nanos) {
            versionSelectionNanos += nanos;
        }

        void addLockfileAssemblyNanos(long nanos) {
            lockfileAssemblyNanos += nanos;
        }

        Map<PackageId, String> projectManagedVersions() {
            Map<PackageId, String> versions = new LinkedHashMap<>();
            projectManagedVersionDetails().forEach((packageId, managedVersion) ->
                    versions.put(packageId, managedVersion.version()));
            return versions;
        }

        Map<PackageId, ManagedVersion> projectManagedVersionDetails() {
            if (projectManagedVersions != null) {
                return projectManagedVersions;
            }
            Map<PackageId, ManagedVersion> details = new LinkedHashMap<>();
            config.platforms().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(platform -> {
                        Coordinate coordinate = coordinateParser.parse(platform.getKey() + ":" + platform.getValue());
                        EffectiveRawPom pom = effectivePom(coordinate, List.of());
                        for (RawPomDependency dependency : pom.dependencyManagement()) {
                            if (dependency.classifier().isPresent()) {
                                continue;
                            }
                            RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                            if (managedJarDependency(interpolated) && interpolated.version().isPresent()) {
                                PackageId packageId = new PackageId(interpolated.groupId(), interpolated.artifactId());
                                details.put(
                                        packageId,
                                        new ManagedVersion(interpolated.version().orElseThrow(), platform.getKey() + ":" + platform.getValue()));
                            }
                        }
                    });
            projectManagedVersions = Map.copyOf(details);
            return projectManagedVersions;
        }

        List<DependencyRequest> projectPlatformPropertiesRequests() {
            List<DependencyRequest> requests = new ArrayList<>();
            config.platforms().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(platform -> {
                        Coordinate coordinate = coordinateParser.parse(platform.getKey() + ":" + platform.getValue());
                        EffectiveRawPom pom = effectivePom(coordinate, List.of());
                        for (RawPomDependency dependency : pom.dependencyManagement()) {
                            if (dependency.classifier().isPresent()) {
                                continue;
                            }
                            RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                            if (!interpolated.type().filter("properties"::equals).isPresent()
                                    || interpolated.version().isEmpty()) {
                                continue;
                            }
                            PackageId packageId = new PackageId(interpolated.groupId(), interpolated.artifactId());
                            Optional<String> version = interpolated.version();
                            requests.add(new DependencyRequest(
                                    packageId,
                                    version.orElseThrow(),
                                    DependencyScope.QUARKUS_DEPLOYMENT,
                                    RequestOrigin.TRANSITIVE,
                                    Optional.of(new ArtifactDescriptor(
                                            new Coordinate(
                                                    packageId.groupId(),
                                                    packageId.artifactId(),
                                                    version),
                                            Optional.empty(),
                                            "properties"))));
                        }
                    });
            return List.copyOf(requests);
        }

        private EffectiveRawPom effectivePom(Coordinate coordinate, List<String> importStack) {
            String key = coordinate.toString();
            EffectiveRawPom cached = metadata.get(key);
            if (cached != null) {
                recordEffectivePomCacheHit();
                return cached;
            }
            if (importStack.contains(key)) {
                throw new ResolveException(
                        "Imported BOM cycle detected: "
                                + String.join(" -> ", importStack)
                                + " -> "
                                + key
                                + ". Remove one of the import-scoped dependencyManagement entries.");
            }

            CompletableFuture<EffectiveRawPom> pending = new CompletableFuture<>();
            CompletableFuture<EffectiveRawPom> existing = metadataLoads.putIfAbsent(key, pending);
            if (existing != null) {
                recordEffectivePomCacheHit();
                return awaitEffectivePom(key, existing);
            }
            recordEffectivePomCacheMiss();
            long started = System.nanoTime();
            try {
                RawPom rawPom = rawPom(coordinate);
                List<RawPom> parents = loadParents(rawPom);
                String groupId = rawPom.groupId().or(() -> nearestGroupId(parents)).orElse(coordinate.groupId());
                String version = rawPom.version()
                        .or(() -> nearestVersion(parents))
                        .orElse(coordinate.version().orElseThrow());
                Map<String, String> properties = inheritedProperties(rawPom, parents);
                List<RawPomDependency> dependencyManagement = inheritedDependencyManagement(rawPom, parents);
                EffectiveRawPom base =
                        new EffectiveRawPom(rawPom, parents, groupId, version, properties, dependencyManagement);
                List<String> nextStack = new ArrayList<>(importStack);
                nextStack.add(key);
                EffectiveRawPom effective = new EffectiveRawPom(
                        rawPom,
                        parents,
                        groupId,
                        version,
                        properties,
                        expandedDependencyManagement(base, nextStack));
                metadata.put(key, effective);
                pending.complete(effective);
                return effective;
            } catch (RuntimeException exception) {
                pending.completeExceptionally(exception);
                throw exception;
            } catch (Error error) {
                pending.completeExceptionally(error);
                throw error;
            } finally {
                metadataLoads.remove(key, pending);
                recordEffectivePomBuild(elapsedSince(started));
            }
        }

        private List<RawPom> loadParents(RawPom rawPom) {
            List<RawPom> nearestFirst = new ArrayList<>();
            RawPom current = rawPom;
            while (current.parent().isPresent()) {
                var parent = current.parent().orElseThrow();
                Coordinate parentCoordinate = new Coordinate(parent.groupId(), parent.artifactId(), Optional.of(parent.version()));
                RawPom parentPom = rawPom(parentCoordinate);
                nearestFirst.add(parentPom);
                current = parentPom;
            }
            List<RawPom> rootFirst = new ArrayList<>();
            for (int index = nearestFirst.size() - 1; index >= 0; index--) {
                rootFirst.add(nearestFirst.get(index));
            }
            return rootFirst;
        }

        private RawPom rawPom(Coordinate coordinate) {
            String key = coordinate.toString();
            RawPom cached = rawPoms.get(key);
            if (cached != null) {
                recordRawPomCacheHit();
                return cached;
            }
            CompletableFuture<RawPom> pending = new CompletableFuture<>();
            CompletableFuture<RawPom> existing = rawPomLoads.putIfAbsent(key, pending);
            if (existing != null) {
                recordRawPomCacheHit();
                return awaitRawPom(key, existing);
            }
            recordRawPomCacheMiss();
            try {
                CachedArtifact pomArtifact = getPom(coordinate);
                long started = System.nanoTime();
                RawPom parsed = rawPomParser.parse(pomArtifact.bytes());
                recordRawPomParse(elapsedSince(started));
                rawPoms.put(key, parsed);
                pending.complete(parsed);
                return parsed;
            } catch (RuntimeException exception) {
                pending.completeExceptionally(exception);
                throw exception;
            } catch (Error error) {
                pending.completeExceptionally(error);
                throw error;
            } finally {
                rawPomLoads.remove(key, pending);
            }
        }

        private com.zolt.maven.RepositoryArtifact fetchPom(Coordinate coordinate) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchPom(access.uri(), coordinate, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchJar(Coordinate coordinate) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchJar(access.uri(), coordinate, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchArtifact(ArtifactDescriptor descriptor) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchArtifact(access.uri(), descriptor, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchFromRepositories(RepositoryFetchAction action) {
            List<RepositoryAccess> repositories = repositoryAccesses();
            RepositoryMissingArtifactException lastMissing = null;
            for (RepositoryAccess repository : repositories) {
                try {
                    return action.fetch(repository);
                } catch (RepositoryMissingArtifactException exception) {
                    lastMissing = exception;
                }
            }
            if (lastMissing != null) {
                throw lastMissing;
            }
            throw new ResolveException(
                    "No repositories are configured in zolt.toml. Add [repositories] with at least one Maven-compatible repository URL.");
        }

        private List<RepositoryAccess> repositoryAccesses() {
            List<RepositorySettings> repositories = config.repositorySettings().values().stream()
                    .sorted(Comparator.comparing(RepositorySettings::id))
                    .toList();
            if (repositories.isEmpty()) {
                throw new ResolveException(
                        "No repositories are configured in zolt.toml. Add [repositories] with at least one Maven-compatible repository URL.");
            }
            List<RepositoryAccess> access = new ArrayList<>();
            for (RepositorySettings repository : repositories) {
                access.add(new RepositoryAccess(
                        URI.create(repository.url()),
                        repository.credentials().map(credentialId -> authentication(repository, credentialId))));
            }
            return List.copyOf(access);
        }

        private RepositoryAuthentication authentication(RepositorySettings repository, String credentialId) {
            RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId);
            if (credential == null) {
                throw new ResolveException(
                        "Repository `"
                                + repository.id()
                                + "` references credentials `"
                                + credentialId
                                + "`, but [repositoryCredentials."
                                + credentialId
                                + "] is not defined.");
            }
            String username = System.getenv(credential.usernameEnv());
            String password = System.getenv(credential.passwordEnv());
            List<String> missing = new ArrayList<>();
            if (username == null || username.isBlank()) {
                missing.add(credential.usernameEnv());
            }
            if (password == null || password.isBlank()) {
                missing.add(credential.passwordEnv());
            }
            if (!missing.isEmpty()) {
                throw new ResolveException(
                        "Repository `"
                                + repository.id()
                                + "` requires credentials `"
                                + credentialId
                                + "`, but environment variable"
                                + (missing.size() == 1 ? " " : "s ")
                                + String.join(", ", missing)
                                + (missing.size() == 1 ? " is" : " are")
                                + " not set. Set the variable"
                                + (missing.size() == 1 ? "" : "s")
                                + " and retry. Secret values are never written to zolt.lock or command output.");
            }
            return new RepositoryAuthentication(username, password);
        }

        private Optional<String> nearestGroupId(List<RawPom> parents) {
            for (int index = parents.size() - 1; index >= 0; index--) {
                if (parents.get(index).groupId().isPresent()) {
                    return parents.get(index).groupId();
                }
            }
            return Optional.empty();
        }

        private Optional<String> nearestVersion(List<RawPom> parents) {
            for (int index = parents.size() - 1; index >= 0; index--) {
                if (parents.get(index).version().isPresent()) {
                    return parents.get(index).version();
                }
            }
            return Optional.empty();
        }

        private Map<String, String> inheritedProperties(RawPom rawPom, List<RawPom> parents) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (RawPom parent : parents) {
                properties.putAll(parent.properties());
            }
            properties.putAll(rawPom.properties());
            return properties;
        }

        private List<RawPomDependency> inheritedDependencyManagement(RawPom rawPom, List<RawPom> parents) {
            List<RawPomDependency> dependencies = new ArrayList<>();
            for (RawPom parent : parents) {
                dependencies.addAll(parent.dependencyManagement());
            }
            dependencies.addAll(rawPom.dependencyManagement());
            return dependencies;
        }

        private List<RawPomDependency> expandedDependencyManagement(EffectiveRawPom pom, List<String> importStack) {
            List<RawPomDependency> dependencies = new ArrayList<>();
            for (RawPomDependency dependency : pom.dependencyManagement()) {
                if (dependency.classifier().isPresent()) {
                    dependencies.add(dependency);
                    continue;
                }
                if (isImportedBom(dependency)) {
                    RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                    Coordinate bomCoordinate = new Coordinate(
                            interpolated.groupId(),
                            interpolated.artifactId(),
                            Optional.of(interpolated.version().orElseThrow(() -> new ResolveException(
                                    "Imported BOM "
                                            + interpolated.groupId()
                                            + ":"
                                            + interpolated.artifactId()
                                            + " in "
                                            + pom.groupId()
                                            + ":"
                                            + pom.rawPom().artifactId()
                                            + " is missing a version. Add a version before resolving."))));
                    EffectiveRawPom imported = effectivePom(bomCoordinate, importStack);
                    for (RawPomDependency importedDependency : imported.dependencyManagement()) {
                        if (importedDependency.classifier().isPresent()) {
                            dependencies.add(importedDependency);
                            continue;
                        }
                        interpolateImportedManagedDependency(importedDependency, imported)
                                .ifPresent(dependencies::add);
                    }
                } else {
                    dependencies.add(dependency);
                }
            }
            return dependencies;
        }

        private static boolean isImportedBom(RawPomDependency dependency) {
            return dependency.type().filter("pom"::equals).isPresent()
                    && dependency.scope().filter("import"::equals).isPresent();
        }

        private Optional<RawPomDependency> interpolateImportedManagedDependency(
                RawPomDependency dependency,
                EffectiveRawPom imported) {
            try {
                return Optional.of(interpolator.interpolateDependency(dependency, imported));
            } catch (PomInterpolationException exception) {
                if (dependency.scope().map(scope -> scope.equals("test") || scope.equals("provided")).orElse(false)) {
                    return Optional.empty();
                }
                throw exception;
            }
        }

        private static boolean managedJarDependency(RawPomDependency dependency) {
            return dependency.type().orElse("jar").equals("jar")
                    && dependency.classifier().isEmpty();
        }
    }
}
