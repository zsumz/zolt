package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockfileReadException;
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
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.quarkus.QuarkusArtifactKey;
import com.zolt.quarkus.QuarkusDeploymentArtifact;
import com.zolt.quarkus.QuarkusExtensionMetadata;
import com.zolt.quarkus.QuarkusExtensionMetadataReader;
import com.zolt.quarkus.QuarkusMetadataException;
import java.io.IOException;
import java.net.URI;
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
import java.util.zip.ZipException;

public final class ResolveService {
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");
    private static final PackageId JUNIT_PLATFORM_CONSOLE_PACKAGE = new PackageId(
            "org.junit.platform",
            "junit-platform-console");
    private static final String JUNIT_PLATFORM_CONSOLE_VERSION = "1.11.4";
    private static final PackageId JACOCO_AGENT_PACKAGE = new PackageId(
            "org.jacoco",
            "org.jacoco.agent");
    private static final PackageId JACOCO_CLI_PACKAGE = new PackageId(
            "org.jacoco",
            "org.jacoco.cli");
    private static final String JACOCO_VERSION = "0.8.14";

    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final DependencyGraphTraverserFactory graphTraverserFactory;
    private final VersionSelector versionSelector;
    private final ZoltLockfileWriter lockfileWriter;
    private final QuarkusExtensionMetadataReader quarkusMetadataReader;

    public ResolveService() {
        this(
                new CoordinateParser(),
                new MavenRepositoryClient(),
                new RawPomParser(),
                DependencyGraphTraverser::new,
                new VersionSelector(),
                new ZoltLockfileWriter(),
                new QuarkusExtensionMetadataReader());
    }

    ResolveService(
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser,
            DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector,
            ZoltLockfileWriter lockfileWriter,
            QuarkusExtensionMetadataReader quarkusMetadataReader) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.graphTraverserFactory = graphTraverserFactory;
        this.versionSelector = versionSelector;
        this.lockfileWriter = lockfileWriter;
        this.quarkusMetadataReader = quarkusMetadataReader;
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
        List<DependencyRequest> directRequests = directRequests(config, managedVersions, options.includeCoverageTooling());
        validateDirectRequestsAllowed(config, directRequests);
        directRequests = relocateDirectRequests(context, directRequests);
        ResolutionState initial = resolveGraph(context, directRequests);
        List<DependencyRequest> allRequests = new ArrayList<>(directRequests);
        if (config.frameworkSettings().quarkus().enabled()) {
            allRequests.addAll(quarkusDeploymentRequests(
                    context,
                    initial.graph(),
                    initial.selection(),
                    directRequests,
                    managedVersions));
            allRequests.addAll(context.projectPlatformPropertiesRequests());
        }
        ResolutionState resolved = allRequests.size() == directRequests.size()
                ? initial
                : resolveGraph(context, allRequests);
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

    private ResolutionState resolveGraph(RepositoryContext context, List<DependencyRequest> requests) {
        DependencyGraphTraverser traverser = graphTraverserFactory.create(context, context.config.dependencyPolicy());
        long traversalStarted = System.nanoTime();
        ResolutionGraph graph = traverser.traverse(requests);
        context.addGraphTraversalNanos(elapsedSince(traversalStarted));
        long selectionStarted = System.nanoTime();
        VersionSelectionResult selection = versionSelector.select(requests, graph);
        context.addVersionSelectionNanos(elapsedSince(selectionStarted));
        return new ResolutionState(graph, selection);
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
            throw new ResolveException(
                    "zolt.lock is out of date. Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.");
        }
    }

    private List<DependencyRequest> directRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            boolean includeCoverageTooling) {
        List<DependencyRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> dependency : config.apiDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "api.dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.COMPILE));
        }
        for (String dependency : config.managedApiDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "api.dependencies",
                    packageId,
                    managedVersion("api.dependencies", packageId, projectManagedVersions),
                    DependencyScope.COMPILE));
        }
        for (Map.Entry<String, String> dependency : config.dependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.COMPILE));
        }
        for (String dependency : config.managedDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "dependencies",
                    packageId,
                    managedVersion("dependencies", packageId, projectManagedVersions),
                    DependencyScope.COMPILE));
        }
        for (Map.Entry<String, String> dependency : config.runtimeDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "runtime.dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.RUNTIME));
        }
        for (String dependency : config.managedRuntimeDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "runtime.dependencies",
                    packageId,
                    managedVersion("runtime.dependencies", packageId, projectManagedVersions),
                    DependencyScope.RUNTIME));
        }
        for (Map.Entry<String, String> dependency : config.providedDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "provided.dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.PROVIDED));
        }
        for (String dependency : config.managedProvidedDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "provided.dependencies",
                    packageId,
                    managedVersion("provided.dependencies", packageId, projectManagedVersions),
                    DependencyScope.PROVIDED));
        }
        for (Map.Entry<String, String> dependency : config.devDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "dev.dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.DEV));
        }
        for (String dependency : config.managedDevDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "dev.dependencies",
                    packageId,
                    managedVersion("dev.dependencies", packageId, projectManagedVersions),
                    DependencyScope.DEV));
        }
        for (Map.Entry<String, String> dependency : config.testDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "test.dependencies",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.TEST));
        }
        for (String dependency : config.managedTestDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "test.dependencies",
                    packageId,
                    managedVersion("test.dependencies", packageId, projectManagedVersions),
                    DependencyScope.TEST));
        }
        for (Map.Entry<String, String> dependency : config.annotationProcessors().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "annotationProcessors",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.PROCESSOR));
        }
        for (String dependency : config.managedAnnotationProcessors()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "annotationProcessors",
                    packageId,
                    managedVersion("annotationProcessors", packageId, projectManagedVersions),
                    DependencyScope.PROCESSOR));
        }
        for (Map.Entry<String, String> dependency : config.testAnnotationProcessors().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    "test.annotationProcessors",
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.TEST_PROCESSOR));
        }
        for (String dependency : config.managedTestAnnotationProcessors()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    "test.annotationProcessors",
                    packageId,
                    managedVersion("test.annotationProcessors", packageId, projectManagedVersions),
                    DependencyScope.TEST_PROCESSOR));
        }
        addTestToolRequests(config, projectManagedVersions, requests);
        addPackageModeRequests(config, projectManagedVersions, requests);
        addOpenApiToolRequests(config, requests);
        if (includeCoverageTooling) {
            addCoverageToolRequests(config, requests);
        }
        return requests;
    }

    private static DependencyRequest directDependencyRequest(
            ProjectConfig config,
            String section,
            PackageId packageId,
            String version,
            DependencyScope scope) {
        DependencyMetadata metadata = config.dependencyMetadata()
                .get(DependencyMetadata.key(section, packageId.toString()));
        if (metadata == null || metadata.exclusions().isEmpty()) {
            return new DependencyRequest(packageId, version, scope, RequestOrigin.DIRECT);
        }
        return new DependencyRequest(
                packageId,
                version,
                scope,
                RequestOrigin.DIRECT,
                metadata.exclusions().stream()
                        .map(exclusion -> new DependencyExclusion(exclusion.group(), exclusion.artifact()))
                        .toList());
    }

    private static void validateDirectRequestsAllowed(
            ProjectConfig config,
            List<DependencyRequest> directRequests) {
        List<DependencyPolicyExclusion> exclusions = config.dependencyPolicy().exclusions();
        if (exclusions.isEmpty()) {
            return;
        }
        for (DependencyRequest request : directRequests) {
            for (DependencyPolicyExclusion exclusion : exclusions) {
                if (exclusion.group().equals(request.packageId().groupId())
                        && exclusion.artifact().equals(request.packageId().artifactId())) {
                    String reason = exclusion.reason()
                            .map(value -> " Reason: " + value + ".")
                            .orElse("");
                    throw new ResolveException(
                            "Dependency policy excludes direct dependency `"
                                    + request.packageId()
                                    + "`."
                                    + reason
                                    + " Remove the direct dependency or remove the matching [dependencyPolicy].exclude entry, then run `zolt resolve` again.");
                }
            }
        }
    }

    private void addTestToolRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (!hasTestInputs(config)) {
            return;
        }
        boolean consoleAlreadyOnTestClasspath = requests.stream()
                .anyMatch(request -> request.packageId().groupId().equals("org.junit.platform")
                        && request.packageId().artifactId().startsWith("junit-platform-console")
                        && request.scope().entersTestClasspath());
        if (consoleAlreadyOnTestClasspath) {
            return;
        }
        String version = projectManagedVersions.getOrDefault(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                JUNIT_PLATFORM_CONSOLE_VERSION);
        if (version == null || version.isBlank()) {
            version = JUNIT_PLATFORM_CONSOLE_VERSION;
        }
        requests.add(new DependencyRequest(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                version,
                DependencyScope.TEST,
                RequestOrigin.TRANSITIVE));
    }

    private static boolean hasTestInputs(ProjectConfig config) {
        return !config.testDependencies().isEmpty()
                || !config.managedTestDependencies().isEmpty()
                || !config.workspaceTestDependencies().isEmpty()
                || !config.testAnnotationProcessors().isEmpty()
                || !config.managedTestAnnotationProcessors().isEmpty();
    }

    private void addPackageModeRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (!isSpringBootArchive(config.packageSettings().mode())) {
            return;
        }
        boolean loaderAlreadyOnMainRuntimeClasspath = requests.stream()
                .anyMatch(request -> request.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)
                        && request.scope().entersMainRuntimeClasspath());
        if (loaderAlreadyOnMainRuntimeClasspath) {
            return;
        }
        String version = projectManagedVersions.get(SPRING_BOOT_LOADER_PACKAGE);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Spring Boot package mode requires package tool artifact `org.springframework.boot:spring-boot-loader`, "
                            + "but no declared [platforms] entry manages its version. Add the Spring Boot platform to [platforms] "
                            + "or declare `org.springframework.boot:spring-boot-loader` with an explicit version, then run `zolt resolve`.");
        }
        requests.add(new DependencyRequest(
                SPRING_BOOT_LOADER_PACKAGE,
                version,
                DependencyScope.RUNTIME,
                RequestOrigin.TRANSITIVE));
    }

    private void addOpenApiToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        List<GeneratedSourceStep> steps = openApiSteps(config);
        if (steps.isEmpty()) {
            return;
        }
        OpenApiGenerationSettings settings = steps.getFirst().openApi();
        String coordinate = settings.toolCoordinate()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        "OpenAPI generation requires [generated.openapiTool].coordinate. "
                                + "Add org.openapitools:openapi-generator-cli with a version, run `zolt resolve`, then retry."));
        String version = settings.toolVersion()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        "OpenAPI generation requires [generated.openapiTool].version for "
                                + coordinate
                                + ". Add an explicit version, run `zolt resolve`, then retry."));
        Coordinate parsed = coordinateParser.parse(coordinate + ":" + version);
        PackageId packageId = PackageId.from(parsed);
        boolean alreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(packageId)
                        && request.scope() == DependencyScope.TOOL_OPENAPI);
        if (alreadyRequested) {
            return;
        }
        requests.add(new DependencyRequest(
                packageId,
                parsed.version().orElseThrow(),
                DependencyScope.TOOL_OPENAPI,
                RequestOrigin.DIRECT));
    }

    private void addCoverageToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        if (!hasTestInputs(config)) {
            return;
        }
        boolean agentAlreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(JACOCO_AGENT_PACKAGE)
                        && request.scope() == DependencyScope.TOOL_COVERAGE);
        if (!agentAlreadyRequested) {
            requests.add(new DependencyRequest(
                    JACOCO_AGENT_PACKAGE,
                    JACOCO_VERSION,
                    DependencyScope.TOOL_COVERAGE,
                    RequestOrigin.TRANSITIVE,
                    Optional.of(ArtifactDescriptor.jar(
                            new Coordinate(
                                    JACOCO_AGENT_PACKAGE.groupId(),
                                    JACOCO_AGENT_PACKAGE.artifactId(),
                                    Optional.of(JACOCO_VERSION)),
                            Optional.of("runtime")))));
        }
        boolean cliAlreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(JACOCO_CLI_PACKAGE)
                        && request.scope() == DependencyScope.TOOL_COVERAGE);
        if (!cliAlreadyRequested) {
            requests.add(new DependencyRequest(
                    JACOCO_CLI_PACKAGE,
                    JACOCO_VERSION,
                    DependencyScope.TOOL_COVERAGE,
                    RequestOrigin.TRANSITIVE));
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

    private static String managedVersion(
            String section,
            PackageId packageId,
            Map<PackageId, String> projectManagedVersions) {
        String version = projectManagedVersions.get(packageId);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Dependency "
                            + packageId
                            + " in ["
                            + section
                            + "] uses a platform-managed version, but no declared [platforms] entry manages it. Add a version or add a platform that manages this dependency.");
        }
        return version;
    }

    private ZoltLockfile lockfile(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        long started = System.nanoTime();
        try {
            Map<PackageId, List<SelectedScope>> selectedScopes = selectedScopes(graph, selection, directRequests);
            List<LockPackagePlan> packagePlans = selection.selectedNodes().stream()
                    .flatMap(node -> selectedScopes
                            .getOrDefault(node.packageId(), List.of(new SelectedScope(DependencyScope.COMPILE, false)))
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
                    packages,
                    conflicts,
                    lockPolicyEffects(graph.policyEffects()));
        } finally {
            context.addLockfileAssemblyNanos(elapsedSince(started));
        }
    }

    private List<DependencyRequest> quarkusDeploymentRequests(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests,
            Map<PackageId, String> managedVersions) {
        Map<PackageId, List<SelectedScope>> selectedScopes = selectedScopes(graph, selection, directRequests);
        Map<PackageId, String> selectedVersions = selectedVersions(selection);
        Map<String, DependencyRequest> requests = new LinkedHashMap<>();
        selection.selectedNodes().stream()
                .sorted(Comparator.comparing(node -> node.packageId() + ":" + node.selectedVersion()))
                .forEach(node -> {
                    boolean runtimeExtensionCandidate = selectedScopes
                            .getOrDefault(node.packageId(), List.of())
                            .stream()
                            .map(SelectedScope::scope)
                            .anyMatch(scope -> scope.entersMainRuntimeClasspath() && scope.packagedByDefault());
                    if (!runtimeExtensionCandidate) {
                        return;
                    }
                    Coordinate coordinate = new Coordinate(
                            node.packageId().groupId(),
                            node.packageId().artifactId(),
                            Optional.of(node.selectedVersion()));
                    CachedArtifact jar = context.getJar(coordinate);
                    Optional<QuarkusExtensionMetadata> metadata = quarkusMetadata(jar.cachePath());
                    metadata.ifPresent(quarkusMetadata -> {
                        QuarkusDeploymentArtifact artifact = quarkusMetadata.deploymentArtifact();
                        if (!"jar".equals(artifact.type())) {
                            throw new ResolveException(
                                    "Quarkus extension "
                                            + coordinate
                                            + " declares deployment artifact "
                                            + artifact
                                            + ", but Zolt currently supports only jar deployment artifacts. "
                                            + "Remove that extension or wait for type-aware artifact resolution.");
                        }
                        DependencyRequest request = new DependencyRequest(
                                new PackageId(artifact.groupId(), artifact.artifactId()),
                                artifact.version(),
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                RequestOrigin.TRANSITIVE,
                                Optional.of(ArtifactDescriptor.jar(
                                        new Coordinate(
                                                artifact.groupId(),
                                                artifact.artifactId(),
                                                Optional.of(artifact.version())),
                                                artifact.classifier())));
                        requests.put(requestKey(request), request);
                        for (QuarkusArtifactKey artifactKey : quarkusMetadata.parentFirstArtifacts()) {
                            parentFirstDeploymentRequest(artifactKey, selectedVersions, managedVersions)
                                    .ifPresent(parentFirstRequest -> requests.put(requestKey(parentFirstRequest), parentFirstRequest));
                        }
                        for (QuarkusArtifactKey artifactKey : quarkusMetadata.runnerParentFirstArtifacts()) {
                            parentFirstDeploymentRequest(artifactKey, selectedVersions, managedVersions)
                                    .ifPresent(parentFirstRequest -> requests.put(requestKey(parentFirstRequest), parentFirstRequest));
                        }
                    });
                });
        return List.copyOf(requests.values());
    }

    private static Map<PackageId, String> selectedVersions(VersionSelectionResult selection) {
        Map<PackageId, String> versions = new LinkedHashMap<>();
        for (PackageNode node : selection.selectedNodes()) {
            versions.put(node.packageId(), node.selectedVersion());
        }
        return versions;
    }

    private static Optional<DependencyRequest> parentFirstDeploymentRequest(
            QuarkusArtifactKey artifactKey,
            Map<PackageId, String> selectedVersions,
            Map<PackageId, String> managedVersions) {
        if (artifactKey.type().isPresent() && !"jar".equals(artifactKey.type().orElseThrow())) {
            return Optional.empty();
        }
        PackageId packageId = new PackageId(artifactKey.groupId(), artifactKey.artifactId());
        String version = selectedVersions.getOrDefault(packageId, managedVersions.get(packageId));
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DependencyRequest(
                packageId,
                version,
                DependencyScope.QUARKUS_DEPLOYMENT,
                RequestOrigin.TRANSITIVE,
                Optional.of(ArtifactDescriptor.jar(
                        new Coordinate(packageId.groupId(), packageId.artifactId(), Optional.of(version)),
                        artifactKey.classifier()))));
    }

    private static String requestKey(DependencyRequest request) {
        return request.packageId()
                + ":"
                + request.requestedVersion()
                + ":"
                + request.scope()
                + ":"
                + request.artifactDescriptor()
                        .flatMap(ArtifactDescriptor::classifier)
                        .orElse("")
                + ":"
                + request.artifactDescriptor()
                        .map(ArtifactDescriptor::extension)
                        .orElse("jar");
    }

    private Optional<QuarkusExtensionMetadata> quarkusMetadata(Path jarPath) {
        try {
            return quarkusMetadataReader.readIfPresent(jarPath);
        } catch (QuarkusMetadataException exception) {
            if (exception.getCause() instanceof ZipException) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Map<PackageId, List<SelectedScope>> selectedScopes(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedScope>> requests = new LinkedHashMap<>();
        List<DependencyRequest> allRequests = new ArrayList<>();
        allRequests.addAll(directRequests);
        allRequests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        for (PackageNode node : selection.selectedNodes()) {
            Map<DependencyScope, SelectedScope> scopesByScope = new LinkedHashMap<>();
            allRequests.stream()
                    .filter(request -> request.packageId().equals(node.packageId()))
                    .forEach(request -> scopesByScope.merge(
                            request.scope(),
                            new SelectedScope(request.scope(), request.direct(), request.artifactDescriptor()),
                            SelectedScope::merge));
            List<SelectedScope> scopes = scopesByScope.values()
                    .stream()
                    .sorted(Comparator.comparing(selectedScope -> selectedScope.scope().lockfileName()))
                    .toList();
            if (!scopes.isEmpty()) {
                requests.put(node.packageId(), scopes);
            }
        }
        return requests;
    }

    private static LockPackagePlan lockPackagePlan(PackageNode node, SelectedScope selectedScope) {
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
        SelectedScope selectedScope = plan.selectedScope();
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
            SelectedScope selectedScope,
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
            SelectedScope selectedScope,
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

    private record SelectedScope(
            DependencyScope scope,
            boolean direct,
            Optional<ArtifactDescriptor> artifactDescriptor) {
        SelectedScope(DependencyScope scope, boolean direct) {
            this(scope, direct, Optional.empty());
        }

        SelectedScope {
            artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
        }

        SelectedScope merge(SelectedScope other) {
            return new SelectedScope(
                    scope,
                    direct || other.direct,
                    artifactDescriptor.isPresent() ? artifactDescriptor : other.artifactDescriptor);
        }
    }

    private record ResolutionState(ResolutionGraph graph, VersionSelectionResult selection) {
    }

    private record LockPackagePlan(
            PackageNode node,
            SelectedScope selectedScope,
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

    private final class RepositoryContext implements DependencyMetadataSource {
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

        void addGraphTraversalNanos(long nanos) {
            graphTraversalNanos += nanos;
        }

        void addVersionSelectionNanos(long nanos) {
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
