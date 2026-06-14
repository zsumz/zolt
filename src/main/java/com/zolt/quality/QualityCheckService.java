package com.zolt.quality;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackagePlanWarning;
import com.zolt.build.PackageEvidenceManifest;
import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackageEvidenceManifestWriter;
import com.zolt.build.PackageException;
import com.zolt.generated.GeneratedSourceEvidence;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.publish.PublishDryRunPlan;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishException;
import com.zolt.publish.PublishRepositorySettings;
import com.zolt.publish.PublishSettings;
import com.zolt.publish.PublishSettingsReader;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.LockfileClasspathPackageConverter;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMemberSelector;
import com.zolt.workspace.WorkspaceSelection;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceResolveService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class QualityCheckService {
    public static final String COMMAND_SURFACE = "command-surface";
    public static final String CACHE_INTEGRITY = "cache-integrity";
    public static final String LOCKFILE = "lockfile";
    public static final String PROJECT_MODEL = "project-model";
    public static final String DEPENDENCY_METADATA = "dependency-metadata";
    public static final String PACKAGE_METADATA = "package-metadata";
    public static final String PACKAGE_CONTENTS = "package-contents";
    public static final String MANIFEST_METADATA = "manifest-metadata";
    public static final String GENERATED_SOURCES = "generated-sources";
    public static final String EXECUTION_CONTEXT = "execution-context";

    private static final Set<String> IMPLEMENTED_CHECKS = Set.of(
            COMMAND_SURFACE,
            CACHE_INTEGRITY,
            EXECUTION_CONTEXT,
            LOCKFILE,
            PROJECT_MODEL,
            DEPENDENCY_METADATA,
            PACKAGE_METADATA,
            PACKAGE_CONTENTS,
            MANIFEST_METADATA,
            GENERATED_SOURCES);
    private static final List<String> CI_CONTEXT_CHECKS = List.of(
            EXECUTION_CONTEXT,
            LOCKFILE,
            PROJECT_MODEL,
            DEPENDENCY_METADATA,
            GENERATED_SOURCES,
            PACKAGE_CONTENTS);
    private static final Map<String, String> PLANNED_CHECK_NOTES = Map.of();
    private static final Set<String> ZOLT_OWNED_MANIFEST_ATTRIBUTES = Set.of(
            "manifest-version",
            "main-class");

    private final ZoltTomlParser projectParser;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceMemberSelector workspaceMemberSelector;
    private final ResolveService resolveService;
    private final WorkspaceResolveService workspaceResolveService;
    private final ZoltLockfileReader lockfileReader;
    private final PackagePlanService packagePlanService;
    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;
    private final PackageEvidenceManifestReader packageEvidenceManifestReader;
    private final PublishDryRunService publishDryRunService;
    private final PublishSettingsReader publishSettingsReader;
    private final Function<String, String> environment;

    public QualityCheckService() {
        this(
                new ZoltTomlParser(),
                new WorkspaceDiscoveryService(),
                new WorkspaceMemberSelector(),
                new ResolveService(),
                new WorkspaceResolveService(),
                new ZoltLockfileReader(),
                new PackagePlanService(),
                new GeneratedSourceEvidenceService(),
                new PackageEvidenceManifestReader(),
                new PublishDryRunService(),
                new PublishSettingsReader(),
                System::getenv);
    }

    QualityCheckService(Function<String, String> environment) {
        this(
                new ZoltTomlParser(),
                new WorkspaceDiscoveryService(),
                new WorkspaceMemberSelector(),
                new ResolveService(),
                new WorkspaceResolveService(),
                new ZoltLockfileReader(),
                new PackagePlanService(),
                new GeneratedSourceEvidenceService(),
                new PackageEvidenceManifestReader(),
                new PublishDryRunService(),
                new PublishSettingsReader(),
                environment);
    }

    QualityCheckService(
            ZoltTomlParser projectParser,
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceMemberSelector workspaceMemberSelector,
            ResolveService resolveService,
            WorkspaceResolveService workspaceResolveService,
            ZoltLockfileReader lockfileReader,
            PackagePlanService packagePlanService,
            GeneratedSourceEvidenceService generatedSourceEvidenceService,
            PackageEvidenceManifestReader packageEvidenceManifestReader,
            PublishDryRunService publishDryRunService,
            PublishSettingsReader publishSettingsReader,
            Function<String, String> environment) {
        this.projectParser = projectParser;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceMemberSelector = workspaceMemberSelector;
        this.resolveService = resolveService;
        this.workspaceResolveService = workspaceResolveService;
        this.lockfileReader = lockfileReader;
        this.packagePlanService = packagePlanService;
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
        this.packageEvidenceManifestReader = packageEvidenceManifestReader;
        this.publishDryRunService = publishDryRunService;
        this.publishSettingsReader = publishSettingsReader;
        this.environment = environment;
    }

    public QualityCheckReport check(QualityCheckRequest request) {
        List<String> requestedChecks = requestedChecks(request);
        Path root = request.projectRoot();

        if (request.workspace()) {
            try {
                Optional<Workspace> maybeWorkspace = workspaceDiscoveryService.discover(root);
                if (maybeWorkspace.isEmpty()) {
                    return new QualityCheckReport(root, true, unavailableResults(
                            requestedChecks,
                            "zolt-workspace.toml",
                            "No Zolt workspace was found for `zolt check --workspace`.",
                            "Run from a workspace root or remove --workspace for a single-project check."));
                }
                Workspace workspace = maybeWorkspace.orElseThrow();
                WorkspaceSelection selection = workspaceMemberSelector.select(workspace, request.workspaceSelection());
                return new QualityCheckReport(root, true, runWorkspaceChecks(request, requestedChecks, workspace, selection));
            } catch (WorkspaceConfigException exception) {
                return new QualityCheckReport(root, true, unavailableResults(
                        requestedChecks,
                        "zolt-workspace.toml",
                        exception.getMessage(),
                        "Fix zolt-workspace.toml or run `zolt check` for a single project."));
            }
        }

        try {
            ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
            return new QualityCheckReport(root, false, runProjectChecks(request, requestedChecks, config));
        } catch (ZoltConfigException exception) {
            return new QualityCheckReport(root, false, unavailableResults(
                    requestedChecks,
                    "zolt.toml",
                    exception.getMessage(),
                    "Fix zolt.toml, then run `zolt check` again."));
        }
    }

    public static Set<String> supportedChecks() {
        Set<String> supported = new LinkedHashSet<>(IMPLEMENTED_CHECKS);
        supported.addAll(new TreeMap<>(PLANNED_CHECK_NOTES).keySet());
        return Collections.unmodifiableSet(supported);
    }

    private static QualityCheckResult commandSurfaceProjectResult(ProjectConfig config) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                config.project().name(),
                "zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.");
    }

    private static QualityCheckResult commandSurfaceWorkspaceResult(Workspace workspace, WorkspaceSelection selection) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                workspace.root().getFileName().toString(),
                "zolt check selected "
                        + selection.includedMembers().size()
                        + " workspace members using typed Zolt workspace data; no Maven, Gradle, or shell hooks are run.");
    }

    private List<QualityCheckResult> runProjectChecks(
            QualityCheckRequest request,
            List<String> requestedChecks,
            ProjectConfig config) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            switch (requestedCheck) {
                case COMMAND_SURFACE -> results.add(commandSurfaceProjectResult(config));
                case CACHE_INTEGRITY -> results.add(checkProjectCacheIntegrity(request));
                case EXECUTION_CONTEXT -> results.addAll(checkExecutionContext(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        request.context(),
                        request.reportsDir(),
                        request.requirePublishDryRun()));
                case LOCKFILE -> results.add(checkProjectLockfile(request, config));
                case PROJECT_MODEL -> results.addAll(checkProjectModel(Optional.empty(), request.projectRoot(), config));
                case DEPENDENCY_METADATA -> results.addAll(checkProjectDependencyMetadata(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        false));
                case PACKAGE_METADATA -> results.add(checkPackageMetadata(
                        Optional.empty(),
                        request.projectRoot(),
                        config));
                case PACKAGE_CONTENTS -> results.addAll(checkPackageContents(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        request.projectRoot().resolve("zolt.lock"),
                        request.requirePackage()));
                case MANIFEST_METADATA -> results.add(checkManifestMetadata(
                        Optional.empty(),
                        config));
                case GENERATED_SOURCES -> results.addAll(checkGeneratedSources(
                        Optional.empty(),
                        request.projectRoot(),
                        config));
                default -> results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> runWorkspaceChecks(
            QualityCheckRequest request,
            List<String> requestedChecks,
            Workspace workspace,
            WorkspaceSelection selection) {
        List<QualityCheckResult> results = new ArrayList<>();
        Map<String, WorkspaceMember> members = membersByPath(workspace);
        for (String requestedCheck : requestedChecks) {
            switch (requestedCheck) {
                case COMMAND_SURFACE -> results.add(commandSurfaceWorkspaceResult(workspace, selection));
                case CACHE_INTEGRITY -> results.add(checkWorkspaceCacheIntegrity(request, workspace));
                case EXECUTION_CONTEXT -> {
                    results.addAll(checkExecutionContext(
                            Optional.empty(),
                            workspace.root(),
                            request.context()));
                    if (request.context() == QualityCheckContext.CI) {
                        for (String memberPath : selection.includedMembers()) {
                            WorkspaceMember member = members.get(memberPath);
                            results.addAll(checkCredentialPolicy(
                                    Optional.of(member.path()),
                                    member.config(),
                                    request.context()));
                            results.addAll(checkPublishCredentialPolicy(
                                    Optional.of(member.path()),
                                    member.directory(),
                                    member.config(),
                                    request.context()));
                            results.addAll(checkResourceTokenInputs(
                                    Optional.of(member.path()),
                                    member.config(),
                                    request.context()));
                            results.addAll(checkTestReports(
                                    Optional.of(member.path()),
                                    member.directory(),
                                    request.reportsDir() == null
                                            ? null
                                            : request.reportsDir().resolve(member.path()),
                                    request.reportsDir(),
                                    request.context()));
                            results.addAll(checkPublishDryRun(
                                    Optional.of(member.path()),
                                    member.directory(),
                                    request.context(),
                                    request.requirePublishDryRun()));
                        }
                    }
                }
                case LOCKFILE -> results.add(checkWorkspaceLockfile(request, workspace));
                case PROJECT_MODEL -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(checkProjectModel(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                case DEPENDENCY_METADATA -> results.addAll(checkWorkspaceDependencyMetadata(workspace, selection, members));
                case PACKAGE_METADATA -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.add(checkPackageMetadata(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                case PACKAGE_CONTENTS -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(checkPackageContents(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config(),
                                workspace.root().resolve("zolt.lock"),
                                request.requirePackage()));
                    }
                }
                case MANIFEST_METADATA -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.add(checkManifestMetadata(
                                Optional.of(member.path()),
                                member.config()));
                    }
                }
                case GENERATED_SOURCES -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(checkGeneratedSources(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                default -> results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> checkExecutionContext(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            QualityCheckContext context,
            Path reportsDir,
            boolean requirePublishDryRun) {
        List<QualityCheckResult> results = new ArrayList<>();
        results.addAll(checkExecutionContext(member, root, context));
        results.addAll(checkCredentialPolicy(member, config, context));
        results.addAll(checkPublishCredentialPolicy(member, root, config, context));
        results.addAll(checkResourceTokenInputs(member, config, context));
        results.addAll(checkTestReports(member, root, reportsDir, reportsDir, context));
        results.addAll(checkPublishDryRun(member, root, context, requirePublishDryRun));
        return List.copyOf(results);
    }

    private QualityCheckResult checkProjectLockfile(QualityCheckRequest request, ProjectConfig config) {
        Path lockfile = request.projectRoot().resolve("zolt.lock");
        boolean requireOfflineReady = request.context() == QualityCheckContext.CI && request.requireOfflineReady();
        boolean offline = request.offline() || requireOfflineReady;
        if (!Files.isRegularFile(lockfile)) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "zolt.lock is missing.",
                    "Run `zolt resolve`.");
        }
        try {
            resolveService.resolve(request.projectRoot(), config, request.cacheRoot(), true, offline);
            return QualityCheckResult.passed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    requireOfflineReady
                            ? "zolt.lock matches zolt.toml and locked artifacts are available from the local cache."
                            : "zolt.lock matches zolt.toml.");
        } catch (ResolveException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve`.");
        } catch (ArtifactCacheException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    requireOfflineReady
                            ? "Run `zolt resolve` to seed the cache, then retry `zolt check --context ci --require-offline-ready`."
                            : "Run `zolt resolve` without --offline to seed the cache, then retry `zolt check --check lockfile --offline`.");
        }
    }

    private QualityCheckResult checkProjectCacheIntegrity(QualityCheckRequest request) {
        return checkCacheIntegrity(Optional.empty(), request.projectRoot().resolve("zolt.lock"), request.cacheRoot());
    }

    private QualityCheckResult checkWorkspaceCacheIntegrity(QualityCheckRequest request, Workspace workspace) {
        return checkCacheIntegrity(Optional.empty(), workspace.root().resolve("zolt.lock"), request.cacheRoot());
    }

    private QualityCheckResult checkCacheIntegrity(
            Optional<String> member,
            Path lockfilePath,
            Path cacheRoot) {
        if (!Files.isRegularFile(lockfilePath)) {
            return QualityCheckResult.failed(
                    CACHE_INTEGRITY,
                    member,
                    "zolt.lock",
                    "zolt.lock is missing.",
                    "Run `zolt resolve`.");
        }
        try {
            ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
            LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot);
            return QualityCheckResult.passed(
                    CACHE_INTEGRITY,
                    member,
                    "zolt.lock",
                    "All cached artifacts with lockfile checksums match local bytes.");
        } catch (LockfileReadException exception) {
            return QualityCheckResult.failed(
                    CACHE_INTEGRITY,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    "Remove the cache entry or run `zolt resolve`.");
        }
    }

    private List<QualityCheckResult> checkExecutionContext(
            Optional<String> member,
            Path root,
            QualityCheckContext context) {
        if (context == null) {
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "default",
                    "No execution context policy was requested."));
        }
        if (context == QualityCheckContext.LOCAL) {
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "local",
                    "Local context policy is active. Policy source: built-in local context. Local overlays are allowed, zolt.lock is not required before editing, and CI/release preflights remain explicit."));
        }
        if (context != QualityCheckContext.CI) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    context.configValue(),
                    "Unsupported execution context `" + context.configValue() + "`.",
                    "Use --context ci for the current Zolt-owned context policy."));
        }
        Path lockfile = root.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfile)) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "ci",
                    "CI context requires zolt.lock before build work starts.",
                    "Run `zolt resolve`, commit zolt.lock, then rerun `zolt check --context ci`."));
        }
        try {
            ZoltLockfile parsed = lockfileReader.read(lockfile);
            Optional<LockPackage> localOverlay = parsed.packages().stream()
                    .filter(lockPackage -> lockPackage.source().startsWith("local-overlay:"))
                    .findFirst();
            if (localOverlay.isPresent()) {
                LockPackage lockPackage = localOverlay.orElseThrow();
                return List.of(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        coordinate(lockPackage),
                        "CI context rejects local repository overlay origin `" + lockPackage.source() + "`.",
                        "Run `zolt resolve --locked --no-local-overlays` or refresh zolt.lock without local overlays."));
            }
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve`, commit zolt.lock, then rerun `zolt check --context ci`."));
        }
        return List.of(QualityCheckResult.passed(
                EXECUTION_CONTEXT,
                member,
                "ci",
                "CI context policy is active. Policy source: built-in ci context. Locked model checks, generated-source checks, package diagnostics, local overlay rejection, and credential preflight are enabled."));
    }

    private List<QualityCheckResult> checkCredentialPolicy(
            Optional<String> member,
            ProjectConfig config,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI) {
            return List.of();
        }

        List<RepositorySettings> repositories = config.repositorySettings().values().stream()
                .sorted(Comparator.comparing(RepositorySettings::id))
                .toList();
        List<QualityCheckResult> results = new ArrayList<>();
        int credentialedRepositories = 0;
        for (RepositorySettings repository : repositories) {
            Optional<QualityCheckResult> embeddedCredentials = embeddedRepositoryCredentials(member, repository);
            if (embeddedCredentials.isPresent()) {
                results.add(embeddedCredentials.orElseThrow());
                continue;
            }
            Optional<String> credentialId = repository.credentials();
            if (credentialId.isEmpty()) {
                continue;
            }
            credentialedRepositories++;
            RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId.orElseThrow());
            if (credential == null) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credentialId.orElseThrow() + "]",
                        "Repository `" + repository.id() + "` references missing credential metadata.",
                        "Define [repositoryCredentials." + credentialId.orElseThrow() + "] with environment variable names, not secret values."));
                continue;
            }

            List<String> missing = missingCredentialEnvironmentVariables(credential);
            if (!missing.isEmpty()) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credential.id() + "]",
                        "CI context requires environment variable"
                                + (missing.size() == 1 ? " " : "s ")
                                + String.join(", ", missing)
                                + " for repository `"
                                + repository.id()
                                + "` credentials `"
                                + credential.id()
                                + "` before resolve/build work starts.",
                        "Set the named CI secret"
                                + (missing.size() == 1 ? "" : "s")
                                + " and rerun `zolt check --context ci`. Secret values are never printed."));
                continue;
            }

            List<String> placeholders = placeholderCredentialEnvironmentVariables(credential);
            if (!placeholders.isEmpty()) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credential.id() + "]",
                        "CI context rejects placeholder credential value"
                                + (placeholders.size() == 1 ? " " : "s ")
                                + "for environment variable"
                                + (placeholders.size() == 1 ? " " : "s ")
                                + String.join(", ", placeholders)
                                + " on repository `"
                                + repository.id()
                                + "`.",
                        "Replace placeholder credentials with real CI secrets. Zolt reports only variable names, never secret values."));
            }
        }

        if (results.isEmpty() && credentialedRepositories > 0) {
            results.add(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "repository-credentials",
                    "CI credential preflight passed for "
                            + credentialedRepositories
                            + " credentialed "
                            + (credentialedRepositories == 1 ? "repository." : "repositories.")));
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> checkPublishCredentialPolicy(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI) {
            return List.of();
        }
        PublishSettings publish = publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
        if (!publish.configured()) {
            return List.of();
        }

        List<PublishRepositorySettings> repositories = publish.repositories().values().stream()
                .sorted(Comparator.comparing(PublishRepositorySettings::id))
                .toList();
        List<QualityCheckResult> results = new ArrayList<>();
        int credentialedRepositories = 0;
        for (PublishRepositorySettings repository : repositories) {
            Optional<QualityCheckResult> embeddedCredentials = embeddedPublishRepositoryCredentials(member, repository);
            if (embeddedCredentials.isPresent()) {
                results.add(embeddedCredentials.orElseThrow());
                continue;
            }
            Optional<String> credentialId = repository.credentials();
            if (credentialId.isEmpty()) {
                continue;
            }
            credentialedRepositories++;
            RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId.orElseThrow());
            if (credential == null) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credentialId.orElseThrow() + "]",
                        "Publish repository `" + repository.id() + "` references missing credential metadata.",
                        "Define [repositoryCredentials." + credentialId.orElseThrow() + "] with environment variable names, not secret values."));
                continue;
            }

            List<String> missing = missingCredentialEnvironmentVariables(credential);
            if (!missing.isEmpty()) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credential.id() + "]",
                        "CI context requires environment variable"
                                + (missing.size() == 1 ? " " : "s ")
                                + String.join(", ", missing)
                                + " for publish repository `"
                                + repository.id()
                                + "` credentials `"
                                + credential.id()
                                + "` before publish work starts.",
                        "Set the named CI secret"
                                + (missing.size() == 1 ? "" : "s")
                                + " and rerun `zolt check --context ci`. Secret values are never printed."));
                continue;
            }

            List<String> placeholders = placeholderCredentialEnvironmentVariables(credential);
            if (!placeholders.isEmpty()) {
                results.add(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        "[repositoryCredentials." + credential.id() + "]",
                        "CI context rejects placeholder credential value"
                                + (placeholders.size() == 1 ? " " : "s ")
                                + "for environment variable"
                                + (placeholders.size() == 1 ? " " : "s ")
                                + String.join(", ", placeholders)
                                + " on publish repository `"
                                + repository.id()
                                + "`.",
                        "Replace placeholder credentials with real CI secrets. Zolt reports only variable names, never secret values."));
            }
        }

        if (results.isEmpty() && credentialedRepositories > 0) {
            results.add(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-credentials",
                    "CI publish credential preflight passed for "
                            + credentialedRepositories
                            + " credentialed publish "
                            + (credentialedRepositories == 1 ? "repository." : "repositories.")));
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> checkResourceTokenInputs(
            Optional<String> member,
            ProjectConfig config,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI) {
            return List.of();
        }
        ResourceFilteringSettings filtering = config.build().resourceFiltering();
        if ((!filtering.enabled() && !filtering.testEnabled()) || filtering.tokens().isEmpty()) {
            return List.of();
        }
        List<QualityCheckResult> failures = new ArrayList<>();
        int envTokens = 0;
        int literalTokens = 0;
        int projectTokens = 0;
        for (Map.Entry<String, ResourceTokenSettings> entry : filtering.tokens().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            ResourceTokenSettings token = entry.getValue();
            if (token.env().isPresent()) {
                envTokens++;
                String env = token.env().orElseThrow();
                if (isMissingEnvironmentValue(env)) {
                    failures.add(QualityCheckResult.failed(
                            EXECUTION_CONTEXT,
                            member,
                            "[resources.tokens." + entry.getKey() + "]",
                            "CI context requires environment variable "
                                    + env
                                    + " for resource token `"
                                    + entry.getKey()
                                    + "` before resource copying.",
                            "Set the named CI variable or change [resources.tokens]."
                                    + entry.getKey()
                                    + " to an explicit non-secret value/project source. Values are never printed."));
                }
            } else if (token.project().isPresent()) {
                projectTokens++;
            } else {
                literalTokens++;
            }
        }
        if (!failures.isEmpty()) {
            return List.copyOf(failures);
        }
        int total = envTokens + literalTokens + projectTokens;
        return List.of(QualityCheckResult.passed(
                EXECUTION_CONTEXT,
                member,
                "resource-token-inputs",
                "CI resource token preflight passed for "
                        + total
                        + " "
                        + (total == 1 ? "token" : "tokens")
                        + ": env="
                        + envTokens
                        + ", project="
                        + projectTokens
                        + ", literal="
                        + literalTokens
                        + "."));
    }

    private Optional<QualityCheckResult> embeddedRepositoryCredentials(
            Optional<String> member,
            RepositorySettings repository) {
        try {
            URI uri = new URI(repository.url());
            if (uri.getUserInfo() == null || uri.getUserInfo().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "[repositories." + repository.id() + "]",
                    "CI context rejects embedded credentials in repository `" + repository.id() + "` URL.",
                    "Move credentials to [repositoryCredentials] environment references. Do not commit username, password, or token values in repository URLs."));
        } catch (URISyntaxException exception) {
            return Optional.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "[repositories." + repository.id() + "]",
                    "Repository `" + repository.id() + "` URL is not a valid URI.",
                    "Edit [repositories." + repository.id() + "] to use a Maven-compatible HTTPS URL without embedded credentials."));
        }
    }

    private Optional<QualityCheckResult> embeddedPublishRepositoryCredentials(
            Optional<String> member,
            PublishRepositorySettings repository) {
        try {
            URI uri = new URI(repository.url());
            if (uri.getUserInfo() == null || uri.getUserInfo().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "[publish.repositories." + repository.id() + "]",
                    "CI context rejects embedded credentials in publish repository `" + repository.id() + "` URL.",
                    "Move publish credentials to [repositoryCredentials] environment references. Do not commit username, password, or token values in publish repository URLs."));
        } catch (URISyntaxException exception) {
            return Optional.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "[publish.repositories." + repository.id() + "]",
                    "Publish repository `" + repository.id() + "` URL is not a valid URI.",
                    "Edit [publish.repositories." + repository.id() + "] to use a Maven-compatible HTTPS URL without embedded credentials."));
        }
    }

    private List<String> missingCredentialEnvironmentVariables(RepositoryCredentialSettings credential) {
        List<String> missing = new ArrayList<>();
        if (isMissingEnvironmentValue(credential.usernameEnv())) {
            missing.add(credential.usernameEnv());
        }
        if (isMissingEnvironmentValue(credential.passwordEnv())) {
            missing.add(credential.passwordEnv());
        }
        return List.copyOf(missing);
    }

    private List<String> placeholderCredentialEnvironmentVariables(RepositoryCredentialSettings credential) {
        List<String> placeholders = new ArrayList<>();
        if (isPlaceholderCredential(environment.apply(credential.usernameEnv()))) {
            placeholders.add(credential.usernameEnv());
        }
        if (isPlaceholderCredential(environment.apply(credential.passwordEnv()))) {
            placeholders.add(credential.passwordEnv());
        }
        return List.copyOf(placeholders);
    }

    private boolean isMissingEnvironmentValue(String name) {
        String value = environment.apply(name);
        return value == null || value.isBlank();
    }

    private static boolean isPlaceholderCredential(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Set.of(
                "read.only",
                "readonly",
                "change-me",
                "changeme",
                "dummy",
                "example",
                "password",
                "secret").contains(normalized);
    }

    private List<QualityCheckResult> checkTestReports(
            Optional<String> member,
            Path projectRoot,
            Path reportsDir,
            Path commandReportsDir,
            QualityCheckContext context) {
        if (context != QualityCheckContext.CI || reportsDir == null) {
            return List.of();
        }
        Path root = ProjectPaths.root(projectRoot);
        Path absoluteReportsDir;
        try {
            absoluteReportsDir = ProjectPaths.output(root, "--reports-dir", reportsDir.toString());
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "--reports-dir",
                    exception.getMessage(),
                    "Use a path such as `target/test-reports`."));
        }
        if (!Files.isDirectory(absoluteReportsDir)) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    displayPath(projectRoot, absoluteReportsDir),
                    "CI context expected JUnit XML reports, but the report directory is missing.",
                    testReportsNextStep(member, commandReportsDir)));
        }
        try {
            long xmlReports = countJUnitXmlReports(root, absoluteReportsDir);
            if (xmlReports == 0) {
                return List.of(QualityCheckResult.failed(
                        EXECUTION_CONTEXT,
                        member,
                        displayPath(projectRoot, absoluteReportsDir),
                        "CI context expected JUnit XML reports, but none were found.",
                        testReportsNextStep(member, commandReportsDir)));
            }
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "test-reports",
                    "CI test report preflight found "
                            + xmlReports
                            + " JUnit XML "
                            + (xmlReports == 1 ? "report." : "reports.")));
        } catch (java.io.IOException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    displayPath(projectRoot, absoluteReportsDir),
                    "Could not inspect JUnit XML reports: " + exception.getMessage(),
                    "Check report directory permissions, then rerun `zolt check --context ci --reports-dir " + commandReportsDir + "`."));
        } catch (ProjectPathException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    displayPath(projectRoot, absoluteReportsDir),
                    exception.getMessage(),
                    "Remove symlinked report entries that escape the project, then rerun `zolt check --context ci --reports-dir " + commandReportsDir + "`."));
        }
    }

    private static String testReportsNextStep(Optional<String> member, Path commandReportsDir) {
        if (member.isPresent()) {
            return "Run `zolt test --workspace --reports-dir "
                    + commandReportsDir
                    + "` before `zolt check --workspace --context ci --reports-dir "
                    + commandReportsDir
                    + "`.";
        }
        return "Run `zolt test --reports-dir "
                + commandReportsDir
                + "` before `zolt check --context ci --reports-dir "
                + commandReportsDir
                + "`.";
    }

    private static long countJUnitXmlReports(Path projectRoot, Path reportsDir) throws java.io.IOException {
        try (Stream<Path> paths = Files.walk(reportsDir)) {
            return paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(projectRoot, "--reports-dir", path))
                    .filter(QualityCheckService::isJUnitXmlReport)
                    .count();
        }
    }

    private static boolean isJUnitXmlReport(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
    }

    private List<QualityCheckResult> checkPublishDryRun(
            Optional<String> member,
            Path projectRoot,
            QualityCheckContext context,
            boolean requirePublishDryRun) {
        if (context != QualityCheckContext.CI || !requirePublishDryRun) {
            return List.of();
        }
        if (member.isPresent()) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight is not available for workspace members yet.",
                    "Run `zolt publish --dry-run` from the publishable member project, or omit --require-publish-dry-run for workspace checks."));
        }
        try {
            PublishDryRunPlan plan = publishDryRunService.plan(projectRoot);
            if (!plan.ok()) {
                List<QualityCheckResult> results = new ArrayList<>();
                for (String blocker : plan.blockers()) {
                    results.add(QualityCheckResult.failed(
                            EXECUTION_CONTEXT,
                            member,
                            "publish-dry-run",
                            "CI publish dry-run blocker: " + blocker,
                            "Run `zolt publish --dry-run` and resolve the reported blocker before release CI."));
                }
                return List.copyOf(results);
            }
            int artifactCount = 1 + plan.supplementalArtifacts().size();
            return List.of(QualityCheckResult.passed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight is ready for "
                            + plan.coordinate()
                            + " to repository `"
                            + plan.repositoryId()
                            + "` with "
                            + artifactCount
                            + " "
                            + (artifactCount == 1 ? "artifact" : "artifacts")
                            + " and generated POM metadata."));
        } catch (PublishException exception) {
            return List.of(QualityCheckResult.failed(
                    EXECUTION_CONTEXT,
                    member,
                    "publish-dry-run",
                    "CI publish dry-run preflight failed: " + exception.getMessage(),
                    "Configure [publish], run `zolt package`, then retry `zolt check --context ci --require-publish-dry-run`."));
        }
    }

    private QualityCheckResult checkWorkspaceLockfile(QualityCheckRequest request, Workspace workspace) {
        Path lockfile = workspace.root().resolve("zolt.lock");
        boolean requireOfflineReady = request.context() == QualityCheckContext.CI && request.requireOfflineReady();
        boolean offline = request.offline() || requireOfflineReady;
        if (!Files.isRegularFile(lockfile)) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock is missing.",
                    "Run `zolt resolve --workspace`.");
        }
        try {
            workspaceResolveService.resolve(workspace.root(), request.cacheRoot(), true, offline);
            return QualityCheckResult.passed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    requireOfflineReady
                            ? "Workspace zolt.lock matches zolt-workspace.toml and member zolt.toml files, and locked artifacts are available from the local cache."
                            : "Workspace zolt.lock matches zolt-workspace.toml and member zolt.toml files.");
        } catch (ResolveException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace`.");
        } catch (ArtifactCacheException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    requireOfflineReady
                            ? "Run `zolt resolve --workspace` to seed the cache, then retry `zolt check --workspace --context ci --require-offline-ready`."
                            : "Run `zolt resolve --workspace` without --offline to seed the cache, then retry `zolt check --workspace --check lockfile --offline`.");
        }
    }

    private static List<QualityCheckResult> checkProjectModel(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        Optional<QualityCheckResult> invalidPath = firstInvalidPath(member, config);
        if (invalidPath.isPresent()) {
            return List.of(invalidPath.orElseThrow());
        }

        Optional<QualityCheckResult> invalidCompilerRelease = invalidCompilerRelease(member, config);
        if (invalidCompilerRelease.isPresent()) {
            return List.of(invalidCompilerRelease.orElseThrow());
        }

        List<QualityCheckResult> results = new ArrayList<>();
        results.add(QualityCheckResult.passed(
                PROJECT_MODEL,
                member,
                config.project().name(),
                "Project model is valid for Zolt-owned checks at " + projectRoot.toAbsolutePath().normalize() + "."));
        results.addAll(unusedVersionAliasDiagnostics(member, config));
        return List.copyOf(results);
    }

    private static List<QualityCheckResult> unusedVersionAliasDiagnostics(
            Optional<String> member,
            ProjectConfig config) {
        if (config.versionAliases().isEmpty()) {
            return List.of();
        }
        Set<String> referencedAliases = referencedVersionAliases(config);
        return config.versionAliases().keySet().stream()
                .filter(alias -> !referencedAliases.contains(alias))
                .sorted()
                .map(alias -> QualityCheckResult.skipped(
                        PROJECT_MODEL,
                        member,
                        "[versions]." + alias,
                        "Version alias `" + alias + "` is declared but not referenced by any versionRef.",
                        "Remove [versions]." + alias + " or update a dependency, platform, processor, constraint, or OpenAPI tool to use versionRef = \"" + alias + "\"."))
                .toList();
    }

    private static Set<String> referencedVersionAliases(ProjectConfig config) {
        Set<String> aliases = new LinkedHashSet<>();
        for (DependencyMetadata metadata : config.dependencyMetadata().values()) {
            if (metadata.versionRef() != null) {
                aliases.add(metadata.versionRef());
            }
        }
        config.dependencyPolicy().constraints().values().stream()
                .flatMap(constraint -> constraint.versionRef().stream())
                .forEach(aliases::add);
        openApiSteps(config).stream()
                .flatMap(step -> step.openApi().toolVersionRef().stream())
                .forEach(aliases::add);
        return Set.copyOf(aliases);
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

    private static Optional<QualityCheckResult> firstInvalidPath(
            Optional<String> member,
            ProjectConfig config) {
        List<PathField> fields = new ArrayList<>();
        BuildSettings build = config.build();
        fields.add(new PathField("[build].source", build.source()));
        fields.add(new PathField("[build].test", build.test()));
        fields.add(new PathField("[build].output", build.output()));
        fields.add(new PathField("[build].testOutput", build.testOutput()));
        addPathFields(fields, "[test.sources].java", build.testSources());
        addPathFields(fields, "[test.sources].groovy", build.groovyTestSources());
        addPathFields(fields, "[resources].main", build.resourceRoots());
        addPathFields(fields, "[resources].test", build.testResourceRoots());
        fields.add(new PathField("[compiler].generatedSources", config.compilerSettings().generatedSources()));
        fields.add(new PathField("[compiler].generatedTestSources", config.compilerSettings().generatedTestSources()));
        addGeneratedPathFields(fields, "[generated.main]", build.generatedMainSources());
        addGeneratedPathFields(fields, "[generated.test]", build.generatedTestSources());

        for (PathField field : fields) {
            if (!isProjectRelative(field.value())) {
                return Optional.of(QualityCheckResult.failed(
                        PROJECT_MODEL,
                        member,
                        field.name(),
                        "Path `" + field.value() + "` must be project-relative and stay inside the project.",
                        "Edit zolt.toml to use a relative path such as `src/main/java` or `target/classes`."));
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidCompilerRelease(
            Optional<String> member,
            ProjectConfig config) {
        String release = config.compilerSettings().release();
        if (release.isBlank()) {
            return Optional.empty();
        }
        Optional<Integer> releaseVersion = javaFeatureVersion(release);
        Optional<Integer> projectVersion = javaFeatureVersion(config.project().java());
        if (releaseVersion.isEmpty()) {
            return Optional.of(QualityCheckResult.failed(
                    PROJECT_MODEL,
                    member,
                    "[compiler].release",
                    "Compiler release `" + release + "` must be a Java feature version.",
                    "Use a numeric release such as `8`, `11`, `17`, or `21`."));
        }
        if (projectVersion.isPresent() && releaseVersion.orElseThrow() > projectVersion.orElseThrow()) {
            return Optional.of(QualityCheckResult.failed(
                    PROJECT_MODEL,
                    member,
                    "[compiler].release",
                    "Compiler release `" + release + "` is newer than [project].java `" + config.project().java() + "`.",
                    "Lower [compiler].release or raise [project].java in zolt.toml."));
        }
        return Optional.empty();
    }

    private static Optional<Integer> javaFeatureVersion(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static void addPathFields(List<PathField> fields, String name, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            fields.add(new PathField(name + "[" + index + "]", values.get(index)));
        }
    }

    private static void addGeneratedPathFields(
            List<PathField> fields,
            String section,
            List<GeneratedSourceStep> steps) {
        for (GeneratedSourceStep step : steps) {
            fields.add(new PathField(section + "." + step.id() + ".output", step.output()));
            addPathFields(fields, section + "." + step.id() + ".inputs", step.inputs());
        }
    }

    private static boolean isProjectRelative(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path path = Path.of(value);
        Path normalized = path.normalize();
        return !path.isAbsolute() && !normalized.startsWith("..");
    }

    private static QualityCheckResult checkPackageMetadata(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        PackageSettings settings = config.packageSettings();
        if (!usesLibraryPackageProfile(settings)) {
            return QualityCheckResult.passed(
                    PACKAGE_METADATA,
                    member,
                    config.project().name(),
                    "No library package metadata is requested.");
        }

        if (!settings.sources()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].sources",
                    "Library package metadata is enabled, but sources jar generation is disabled.",
                    "Set [package].sources = true for library projects.");
        }
        if (hasSourceFiles(projectRoot, List.of(config.build().source())) && !settings.javadoc()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].javadoc",
                    "Library package metadata is enabled, but javadoc jar generation is disabled.",
                    "Set [package].javadoc = true when publishing Java APIs.");
        }
        if (hasSourceFiles(projectRoot, testSourceRoots(config.build())) && !settings.tests()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].tests",
                    "Test sources are present, but tests jar generation is disabled for this library package.",
                    "Set [package].tests = true or remove test sources from the library artifact story.");
        }

        Optional<QualityCheckResult> missingMetadata = firstMissingPublicationMetadata(member, settings.metadata());
        if (missingMetadata.isPresent()) {
            return missingMetadata.orElseThrow();
        }

        return QualityCheckResult.passed(
                PACKAGE_METADATA,
                member,
                config.project().name(),
                "Library package metadata is complete.");
    }

    private List<QualityCheckResult> checkPackageContents(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config,
            Path lockfilePath,
            boolean requirePackage) {
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    "zolt.lock",
                    "Package content diagnostics require zolt.lock.",
                    member.isPresent() ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }
        PackagePlan plan;
        try {
            plan = packagePlanService.plan(projectRoot, config, lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    member.isPresent() ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }
        if (plan.warnings().isEmpty()) {
            if (requirePackage && !Files.isRegularFile(plan.archivePath())) {
                return List.of(QualityCheckResult.failed(
                        PACKAGE_CONTENTS,
                        member,
                        displayPath(projectRoot, plan.archivePath()),
                        "CI context requires the configured package artifact, but it is missing.",
                        "Run `zolt package` before `zolt check --context ci --require-package`."));
            }
            Optional<QualityCheckResult> staleEvidence = stalePackageEvidence(member, plan);
            if (staleEvidence.isPresent()) {
                return List.of(staleEvidence.orElseThrow());
            }
            long policyEffects = plan.dependencies().stream()
                    .filter(dependency -> !dependency.policies().isEmpty())
                    .count();
            String policyMessage = policyEffects == 0
                    ? ""
                    : " " + policyEffects + " dependencies include dependency policy effects.";
            return List.of(QualityCheckResult.passed(
                    PACKAGE_CONTENTS,
                    member,
                    config.project().name(),
                    "Package mode `"
                            + plan.mode().configValue()
                            + "` has "
                            + plan.dependencies().size()
                            + " dependency dispositions."
                            + policyMessage));
        }
        List<QualityCheckResult> results = new ArrayList<>();
        for (PackagePlanWarning warning : plan.warnings()) {
            results.add(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    warning.subject(),
                    warning.message(),
                    warning.nextStep()));
        }
        return List.copyOf(results);
    }

    private Optional<QualityCheckResult> stalePackageEvidence(Optional<String> member, PackagePlan plan) {
        Path archive = plan.archivePath();
        if (!Files.exists(archive)) {
            return Optional.empty();
        }
        Path root = plan.projectRoot().toAbsolutePath().normalize();
        Path manifestPath = PackageEvidenceManifestWriter.evidenceManifestPath(archive);
        if (!Files.isRegularFile(manifestPath)) {
            return Optional.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    displayPath(root, archive),
                    "Package artifact exists, but package evidence manifest is missing.",
                    "Run `zolt package` to regenerate " + displayPath(root, manifestPath) + "."));
        }
        try {
            PackageEvidenceManifest manifest = packageEvidenceManifestReader.read(manifestPath);
            String actualSha256 = sha256(archive);
            if (!actualSha256.equals(manifest.archiveSha256())) {
                return Optional.of(QualityCheckResult.failed(
                        PACKAGE_CONTENTS,
                        member,
                        displayPath(root, manifestPath),
                        "Package evidence manifest is stale for `" + displayPath(root, archive) + "`.",
                        "Run `zolt package` to regenerate the artifact and evidence manifest."));
            }
        } catch (PackageException exception) {
            return Optional.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    displayPath(root, manifestPath),
                    exception.getMessage(),
                    "Run `zolt package` to regenerate package evidence."));
        }
        return Optional.empty();
    }

    private static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.io.IOException exception) {
            throw new PackageException(
                    "Could not read package artifact at "
                            + path
                            + ". Check that the file is readable and retry.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PackageException("Could not compute package artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    private static String displayPath(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    private static QualityCheckResult checkManifestMetadata(
            Optional<String> member,
            ProjectConfig config) {
        PackageSettings settings = config.packageSettings();
        for (String attributeName : settings.manifestAttributes().keySet()) {
            if (ZOLT_OWNED_MANIFEST_ATTRIBUTES.contains(attributeName.toLowerCase(Locale.ROOT))) {
                return QualityCheckResult.failed(
                        MANIFEST_METADATA,
                        member,
                        "[package.manifest]." + attributeName,
                        "Manifest attribute `" + attributeName + "` is owned by Zolt.",
                        "Remove it from [package.manifest]; use [project].main for Main-Class.");
            }
        }

        if (!usesLibraryPackageProfile(settings)) {
            return QualityCheckResult.passed(
                    MANIFEST_METADATA,
                    member,
                config.project().name(),
                "No library manifest metadata is requested.");
        }

        if (!containsManifestAttribute(settings, "Automatic-Module-Name")) {
            return QualityCheckResult.failed(
                    MANIFEST_METADATA,
                    member,
                    "[package.manifest].Automatic-Module-Name",
                    "Library package metadata is enabled, but Automatic-Module-Name is missing.",
                    "Add [package.manifest].\"Automatic-Module-Name\" with a stable Java module name.");
        }

        return QualityCheckResult.passed(
                MANIFEST_METADATA,
                member,
                config.project().name(),
                "Library manifest metadata is deterministic.");
    }

    private static boolean containsManifestAttribute(PackageSettings settings, String name) {
        return settings.manifestAttributes().keySet().stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(name));
    }

    private static boolean usesLibraryPackageProfile(PackageSettings settings) {
        return settings.sources()
                || settings.javadoc()
                || settings.tests()
                || hasPublicationMetadata(settings.metadata())
                || !settings.manifestAttributes().isEmpty();
    }

    private static boolean hasPublicationMetadata(PublicationMetadata metadata) {
        return !metadata.name().isBlank()
                || !metadata.description().isBlank()
                || !metadata.url().isBlank()
                || !metadata.license().isBlank()
                || !metadata.developers().isEmpty()
                || !metadata.scm().isBlank()
                || !metadata.issues().isBlank();
    }

    private static Optional<QualityCheckResult> firstMissingPublicationMetadata(
            Optional<String> member,
            PublicationMetadata metadata) {
        if (metadata.name().isBlank()) {
            return missingPublicationField(member, "name");
        }
        if (metadata.description().isBlank()) {
            return missingPublicationField(member, "description");
        }
        if (metadata.url().isBlank()) {
            return missingPublicationField(member, "url");
        }
        if (metadata.license().isBlank()) {
            return missingPublicationField(member, "license");
        }
        if (metadata.developers().isEmpty()) {
            return missingPublicationField(member, "developers");
        }
        if (metadata.scm().isBlank()) {
            return missingPublicationField(member, "scm");
        }
        if (metadata.issues().isBlank()) {
            return missingPublicationField(member, "issues");
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> missingPublicationField(Optional<String> member, String field) {
        return Optional.of(QualityCheckResult.failed(
                PACKAGE_METADATA,
                member,
                "[package.metadata]." + field,
                "Library package metadata is enabled, but publication metadata field `" + field + "` is missing.",
                "Fill [package.metadata]." + field + " in zolt.toml."));
    }

    private static List<String> testSourceRoots(BuildSettings build) {
        List<String> roots = new ArrayList<>();
        roots.add(build.test());
        roots.addAll(build.testSources());
        roots.addAll(build.groovyTestSources());
        return List.copyOf(new LinkedHashSet<>(roots));
    }

    private static boolean hasSourceFiles(Path projectRoot, List<String> roots) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path sourceRoot = normalizedRoot.resolve(root).normalize();
            if (!sourceRoot.startsWith(normalizedRoot) || !Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var stream = Files.find(sourceRoot, Integer.MAX_VALUE, (path, attributes) ->
                    attributes.isRegularFile() && sourceLike(path))) {
                if (stream.findFirst().isPresent()) {
                    return true;
                }
            } catch (java.io.IOException exception) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceLike(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".groovy");
    }

    private List<QualityCheckResult> checkGeneratedSources(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        List<GeneratedSourceCheckStep> steps = generatedSourceSteps(config.build());
        if (steps.isEmpty()) {
            return List.of(QualityCheckResult.passed(
                    GENERATED_SOURCES,
                    member,
                    config.project().name(),
                    "No declared generated-source steps require validation."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Map<String, GeneratedSourceEvidence> evidenceByKey = generatedSourceEvidenceByKey(normalizedRoot, config);
        for (GeneratedSourceCheckStep checkStep : steps) {
            GeneratedSourceStep step = checkStep.step();
            Optional<QualityCheckResult> invalid = invalidGeneratedSourceStep(member, normalizedRoot, checkStep);
            if (invalid.isPresent()) {
                results.add(invalid.orElseThrow());
                continue;
            }

            String subject = generatedSection(checkStep);
            GeneratedSourceEvidence evidence = evidenceByKey.get(generatedSourceKey(checkStep.scope(), step.id()));
            Optional<String> missingInput = firstMissingGeneratedInput(step, evidence);
            if (missingInput.isPresent()) {
                results.add(QualityCheckResult.failed(
                        GENERATED_SOURCES,
                        member,
                        subject,
                        "Generated source input `" + missingInput.orElseThrow() + "` is missing.",
                        "Create the input file or update " + subject + ".inputs."));
                continue;
            }
            if (!evidence.outputExists()) {
                if (step.required()) {
                    results.add(QualityCheckResult.failed(
                            GENERATED_SOURCES,
                            member,
                            subject,
                            "Generated source root `" + step.output() + "` is missing.",
                            "Run the generator that produces it, commit the generated sources, or remove "
                                    + subject
                                    + " until Zolt supports that generator."));
                    continue;
                }
                results.add(QualityCheckResult.skipped(
                        GENERATED_SOURCES,
                        member,
                        subject,
                        "Optional generated source root `" + step.output() + "` is missing.",
                        "Generate it when needed, or set required = true if the root must exist for builds."));
                continue;
            }
            if ("stale".equals(evidence.freshness())) {
                results.add(QualityCheckResult.failed(
                        GENERATED_SOURCES,
                        member,
                        subject,
                        "Generated source root `" + step.output() + "` is stale; one or more declared inputs are newer.",
                        "Regenerate the source root or update " + subject + ".inputs."));
                continue;
            }

            results.add(QualityCheckResult.passed(
                    GENERATED_SOURCES,
                    member,
                    subject,
                    "Generated source root `"
                            + step.output()
                            + "` is declared and exported as IDE source root `generated-"
                            + checkStep.scope()
                            + "-"
                            + step.id()
                            + "` with ownership `"
                            + evidence.ownership()
                            + "` and freshness `"
                            + evidence.freshness()
                            + "`."));
        }
        return List.copyOf(results);
    }

    private Map<String, GeneratedSourceEvidence> generatedSourceEvidenceByKey(Path projectRoot, ProjectConfig config) {
        Map<String, GeneratedSourceEvidence> evidence = new LinkedHashMap<>();
        for (GeneratedSourceEvidence generatedSource : generatedSourceEvidenceService.evidence(projectRoot, config.build())) {
            evidence.put(generatedSourceKey(generatedSource.scope(), generatedSource.step().id()), generatedSource);
        }
        return Map.copyOf(evidence);
    }

    private static String generatedSourceKey(String scope, String id) {
        return scope + ":" + id;
    }

    private static Optional<String> firstMissingGeneratedInput(
            GeneratedSourceStep step,
            GeneratedSourceEvidence evidence) {
        for (int index = 0; index < step.inputs().size(); index++) {
            if (!Files.exists(evidence.inputs().get(index))) {
                return Optional.of(step.inputs().get(index));
            }
        }
        return Optional.empty();
    }

    private static List<GeneratedSourceCheckStep> generatedSourceSteps(BuildSettings build) {
        List<GeneratedSourceCheckStep> steps = new ArrayList<>();
        for (GeneratedSourceStep step : build.generatedMainSources()) {
            steps.add(new GeneratedSourceCheckStep("main", step));
        }
        for (GeneratedSourceStep step : build.generatedTestSources()) {
            steps.add(new GeneratedSourceCheckStep("test", step));
        }
        return List.copyOf(steps);
    }

    private static Optional<QualityCheckResult> invalidGeneratedSourceStep(
            Optional<String> member,
            Path projectRoot,
            GeneratedSourceCheckStep checkStep) {
        GeneratedSourceStep step = checkStep.step();
        String subject = generatedSection(checkStep);
        if (step.kind() != GeneratedSourceKind.DECLARED_ROOT && step.kind() != GeneratedSourceKind.OPENAPI) {
            return Optional.of(QualityCheckResult.failed(
                    GENERATED_SOURCES,
                    member,
                    subject,
                    "Unsupported generated source kind `" + step.kind().configValue() + "`.",
                    "Use declared-root for already generated Java sources."));
        }
        if (!"java".equals(step.language())) {
            return Optional.of(QualityCheckResult.failed(
                    GENERATED_SOURCES,
                    member,
                    subject,
                    "Unsupported generated source language `" + step.language() + "`.",
                    "Use language = \"java\" for MVP generated-source steps."));
        }
        Optional<QualityCheckResult> invalidOutput = invalidGeneratedPath(
                member,
                projectRoot,
                checkStep,
                "output",
                step.output());
        if (invalidOutput.isPresent()) {
            return invalidOutput;
        }
        for (String input : step.inputs()) {
            Optional<QualityCheckResult> invalidInput = invalidGeneratedPath(
                    member,
                    projectRoot,
                    checkStep,
                    "inputs",
                    input);
            if (invalidInput.isPresent()) {
                return invalidInput;
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidGeneratedPath(
            Optional<String> member,
            Path projectRoot,
            GeneratedSourceCheckStep checkStep,
            String field,
            String configuredPath) {
        Path configured = Path.of(configuredPath);
        Path resolved = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !resolved.startsWith(projectRoot) || resolved.equals(projectRoot)) {
            return Optional.of(QualityCheckResult.failed(
                    GENERATED_SOURCES,
                    member,
                    generatedSection(checkStep) + "." + field,
                    "Invalid generated source " + field + " path `" + configuredPath + "`.",
                    "Use a project-relative path under the project directory."));
        }
        return Optional.empty();
    }

    private static String generatedSection(GeneratedSourceCheckStep checkStep) {
        return "[generated." + checkStep.scope() + "." + checkStep.step().id() + "]";
    }

    private List<QualityCheckResult> checkProjectDependencyMetadata(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            boolean workspaceLockfile) {
        Path lockfilePath = root.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    (workspaceLockfile ? "Workspace zolt.lock" : "zolt.lock") + " is missing.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        return checkDependencyMetadataDeclarations(member, config, lockfile, workspaceLockfile);
    }

    private List<QualityCheckResult> checkWorkspaceDependencyMetadata(
            Workspace workspace,
            WorkspaceSelection selection,
            Map<String, WorkspaceMember> members) {
        Path lockfilePath = workspace.root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock is missing.",
                    "Run `zolt resolve --workspace`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace`."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = members.get(memberPath);
            results.addAll(checkDependencyMetadataDeclarations(
                    Optional.of(member.path()),
                    member.config(),
                    lockfile,
                    true));
            results.addAll(checkWorkspaceApiEdges(workspace, member, lockfile));
        }
        if (results.isEmpty()) {
            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    workspace.config().name(),
                    "No dependency metadata declarations require validation."));
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> checkDependencyMetadataDeclarations(
            Optional<String> member,
            ProjectConfig config,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        if (config.dependencyMetadata().isEmpty()) {
            return List.of(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    member,
                    config.project().name(),
                    "No dependency metadata declarations require validation."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (DependencyMetadata metadata : new TreeMap<>(config.dependencyMetadata()).values()) {
            if (metadata.workspace() != null) {
                if (metadata.optional()) {
                    results.add(QualityCheckResult.failed(
                            DEPENDENCY_METADATA,
                            member,
                            metadata.coordinate(),
                            "Workspace dependency `" + metadata.coordinate() + "` declares optional metadata, which is not supported.",
                            "Remove optional = true or use an external dependency coordinate."));
                }
                continue;
            }

            if (metadata.publishOnly()) {
                results.add(checkPublishOnlyMetadata(member, metadata, lockfile));
                continue;
            }

            results.add(checkClasspathMetadata(member, metadata, lockfile, workspaceLockfile));
        }
        return List.copyOf(results);
    }

    private QualityCheckResult checkPublishOnlyMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile) {
        Optional<LockPackage> lockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (lockPackage.isPresent()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Publish-only dependency `" + metadata.coordinate() + "` is present in zolt.lock.",
                    "Run `zolt resolve`; if it remains, remove publishOnly = true or move the dependency to a normal classpath section.");
        }
        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Publish-only dependency `" + metadata.coordinate() + "` is kept out of zolt.lock classpaths.");
    }

    private QualityCheckResult checkClasspathMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        Optional<LockPackage> maybeLockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (maybeLockPackage.isEmpty()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Dependency metadata for `" + metadata.coordinate() + "` is not represented in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        LockPackage lockPackage = maybeLockPackage.orElseThrow();
        if (metadata.optional() && !lockPackage.direct()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Optional direct dependency `" + metadata.coordinate() + "` is not marked direct in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        for (com.zolt.project.DependencyExclusionSpec exclusion : metadata.exclusions()) {
            if (lockPackage.dependencies().contains(exclusion.coordinate())) {
                return QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        member,
                        metadata.coordinate(),
                        "Excluded dependency `" + exclusion.coordinate() + "` is still present on direct dependency `" + metadata.coordinate() + "` in zolt.lock.",
                        "Check [" + metadata.section() + "]." + metadata.coordinate() + ".exclusions and run "
                                + (workspaceLockfile ? "`zolt resolve --workspace`." : "`zolt resolve`."));
            }
        }

        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Dependency metadata for `" + metadata.coordinate() + "` is represented in zolt.lock.");
    }

    private static List<QualityCheckResult> checkWorkspaceApiEdges(
            Workspace workspace,
            WorkspaceMember member,
            ZoltLockfile lockfile) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (Map.Entry<String, String> dependency : new TreeMap<>(member.config().workspaceApiDependencies()).entrySet()) {
            String coordinate = dependency.getKey();
            String target = normalizeMemberPath(dependency.getValue());
            Optional<com.zolt.workspace.WorkspaceProjectEdge> edge = workspace.edges().stream()
                    .filter(candidate -> candidate.from().equals(member.path())
                            && candidate.to().equals(target)
                            && candidate.coordinate().equals(coordinate))
                    .findFirst();
            if (edge.isEmpty() || !edge.orElseThrow().exported()) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is not represented as an exported workspace edge.",
                        "Keep public workspace dependencies in [api.dependencies] and run `zolt resolve --workspace`."));
                continue;
            }

            Optional<LockPackage> packageNode = lockfile.packages().stream()
                    .filter(lockPackage -> lockPackage.packageId().equals(packageId(coordinate))
                            && lockPackage.workspace().orElse("").equals(target))
                    .findFirst();
            if (packageNode.isEmpty() || !packageNode.orElseThrow().exportedBy().contains(member.path())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is missing exportedBy ownership in zolt.lock.",
                        "Run `zolt resolve --workspace`."));
                continue;
            }

            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.of(member.path()),
                    coordinate,
                    "Workspace API dependency `" + coordinate + "` is exported through zolt.lock."));
        }
        return List.copyOf(results);
    }

    private static Optional<LockPackage> findLockPackage(
            ZoltLockfile lockfile,
            PackageId packageId,
            Optional<String> member) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .filter(lockPackage -> member.isEmpty()
                        || lockPackage.members().isEmpty()
                        || lockPackage.members().contains(member.orElseThrow()))
                .findFirst();
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new PackageId(parts[0], parts[1]);
    }

    private static String normalizeMemberPath(String path) {
        String normalized = Path.of(path).normalize().toString().replace('\\', '/');
        return normalized.isBlank() ? "." : normalized;
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return Collections.unmodifiableMap(members);
    }

    private static List<QualityCheckResult> unavailableResults(
            List<String> requestedChecks,
            String subject,
            String message,
            String nextStep) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            if (IMPLEMENTED_CHECKS.contains(requestedCheck)) {
                results.add(QualityCheckResult.failed(
                        requestedCheck,
                        Optional.empty(),
                        subject,
                        message,
                        nextStep));
            } else {
                results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private static QualityCheckResult unsupportedOrSkipped(String requestedCheck) {
        if (PLANNED_CHECK_NOTES.containsKey(requestedCheck)) {
            return QualityCheckResult.skipped(
                    requestedCheck,
                    Optional.empty(),
                    requestedCheck,
                    "Quality check `" + requestedCheck + "` is planned but not implemented yet.",
                    "Track " + PLANNED_CHECK_NOTES.get(requestedCheck) + ".");
        }
        return QualityCheckResult.failed(
                "unsupported-check",
                Optional.empty(),
                requestedCheck,
                "Unsupported quality check `" + requestedCheck + "`.",
                "Use one of: " + String.join(", ", supportedChecks()) + ". Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks.");
    }

    private static List<String> requestedChecks(QualityCheckRequest request) {
        List<String> rawChecks = request.checks();
        if (rawChecks.isEmpty()) {
            if (request.context() == QualityCheckContext.CI) {
                return CI_CONTEXT_CHECKS;
            }
            if (request.context() == QualityCheckContext.LOCAL) {
                return List.of(EXECUTION_CONTEXT);
            }
            return List.of(COMMAND_SURFACE);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (request.context() == QualityCheckContext.CI || request.context() == QualityCheckContext.LOCAL) {
            normalized.add(EXECUTION_CONTEXT);
        }
        for (String rawCheck : rawChecks) {
            String check = rawCheck == null ? "" : rawCheck.trim();
            if (!check.isEmpty()) {
                normalized.add(check);
            }
        }
        return List.copyOf(normalized);
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private record PathField(String name, String value) {
    }

    private record GeneratedSourceCheckStep(String scope, GeneratedSourceStep step) {
    }
}
