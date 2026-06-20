package com.zolt.quality;

import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackagePlanService;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.project.ProjectConfig;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishSettingsReader;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceMemberSelector;
import com.zolt.workspace.WorkspaceResolveService;
import com.zolt.workspace.WorkspaceSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class QualityCheckService {
    public static final String COMMAND_SURFACE = QualityCheckCatalog.COMMAND_SURFACE;
    public static final String CACHE_INTEGRITY = QualityCheckCatalog.CACHE_INTEGRITY;
    public static final String LOCKFILE = QualityCheckCatalog.LOCKFILE;
    public static final String PROJECT_MODEL = QualityCheckCatalog.PROJECT_MODEL;
    public static final String DEPENDENCY_METADATA = QualityCheckCatalog.DEPENDENCY_METADATA;
    public static final String DEPENDENCY_POLICY = QualityCheckCatalog.DEPENDENCY_POLICY;
    public static final String PACKAGE_METADATA = QualityCheckCatalog.PACKAGE_METADATA;
    public static final String PACKAGE_CONTENTS = QualityCheckCatalog.PACKAGE_CONTENTS;
    public static final String MANIFEST_METADATA = QualityCheckCatalog.MANIFEST_METADATA;
    public static final String GENERATED_SOURCES = QualityCheckCatalog.GENERATED_SOURCES;
    public static final String EXECUTION_CONTEXT = QualityCheckCatalog.EXECUTION_CONTEXT;

    private final ZoltTomlParser projectParser;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceMemberSelector workspaceMemberSelector;
    private final GeneratedSourceQualityCheck generatedSourceQualityCheck;
    private final LockfileQualityCheck lockfileQualityCheck;
    private final QualityExecutionContextRunner executionContextRunner;
    private final ProjectModelQualityCheck projectModelQualityCheck;
    private final PackageQualityCheck packageQualityCheck;
    private final DependencyQualityCheck dependencyQualityCheck;

    public QualityCheckService() {
        this(
                new ZoltTomlParser(),
                new WorkspaceDiscoveryService(),
                new WorkspaceMemberSelector(),
                new ResolveService(),
                new WorkspaceResolveService(),
                new ZoltLockfileReader(),
                new PackagePlanService(),
                new DependencyPolicyReportService(),
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
                new DependencyPolicyReportService(),
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
            DependencyPolicyReportService dependencyPolicyReportService,
            GeneratedSourceEvidenceService generatedSourceEvidenceService,
            PackageEvidenceManifestReader packageEvidenceManifestReader,
            PublishDryRunService publishDryRunService,
            PublishSettingsReader publishSettingsReader,
            Function<String, String> environment) {
        this.projectParser = projectParser;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceMemberSelector = workspaceMemberSelector;
        this.generatedSourceQualityCheck = new GeneratedSourceQualityCheck(generatedSourceEvidenceService);
        this.lockfileQualityCheck = new LockfileQualityCheck(resolveService, workspaceResolveService, lockfileReader);
        this.executionContextRunner = new QualityExecutionContextRunner(
                new ExecutionContextQualityCheck(lockfileReader),
                new CredentialQualityCheck(publishSettingsReader, environment),
                new ExecutionEvidenceQualityCheck(),
                new PublishDryRunQualityCheck(publishDryRunService));
        this.projectModelQualityCheck = new ProjectModelQualityCheck();
        this.packageQualityCheck = new PackageQualityCheck(packagePlanService, packageEvidenceManifestReader);
        this.dependencyQualityCheck = new DependencyQualityCheck(lockfileReader, dependencyPolicyReportService);
    }

    public QualityCheckReport check(QualityCheckRequest request) {
        List<String> requestedChecks = QualityCheckCatalog.requestedChecks(request);
        Path root = request.projectRoot();

        if (request.workspace()) {
            try {
                Optional<Workspace> maybeWorkspace = workspaceDiscoveryService.discover(root);
                if (maybeWorkspace.isEmpty()) {
                    return new QualityCheckReport(root, true, QualityCheckCatalog.unavailableResults(
                            requestedChecks,
                            "zolt-workspace.toml",
                            "No Zolt workspace was found for `zolt check --workspace`.",
                            "Run from a workspace root or remove --workspace for a single-project check."));
                }
                Workspace workspace = maybeWorkspace.orElseThrow();
                WorkspaceSelection selection = workspaceMemberSelector.select(workspace, request.workspaceSelection());
                return new QualityCheckReport(root, true, runWorkspaceChecks(request, requestedChecks, workspace, selection));
            } catch (WorkspaceConfigException exception) {
                return new QualityCheckReport(root, true, QualityCheckCatalog.unavailableResults(
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
            return new QualityCheckReport(root, false, QualityCheckCatalog.unavailableResults(
                    requestedChecks,
                    "zolt.toml",
                    exception.getMessage(),
                    "Fix zolt.toml, then run `zolt check` again."));
        }
    }

    public static Set<String> supportedChecks() {
        return QualityCheckCatalog.supportedChecks();
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
                case CACHE_INTEGRITY -> results.add(lockfileQualityCheck.checkProjectCacheIntegrity(request));
                case EXECUTION_CONTEXT -> results.addAll(executionContextRunner.checkProject(request, config));
                case LOCKFILE -> results.add(lockfileQualityCheck.checkProjectLockfile(request, config));
                case PROJECT_MODEL -> results.addAll(projectModelQualityCheck.check(
                        Optional.empty(),
                        request.projectRoot(),
                        config));
                case DEPENDENCY_METADATA -> results.addAll(dependencyQualityCheck.checkProjectMetadata(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        false));
                case DEPENDENCY_POLICY -> results.addAll(dependencyQualityCheck.checkPolicy(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        request.projectRoot().resolve("zolt.lock"),
                        false));
                case PACKAGE_METADATA -> results.add(packageQualityCheck.checkMetadata(
                        Optional.empty(),
                        request.projectRoot(),
                        config));
                case PACKAGE_CONTENTS -> results.addAll(packageQualityCheck.checkContents(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        request.projectRoot().resolve("zolt.lock"),
                        request.requirePackage()));
                case MANIFEST_METADATA -> results.add(packageQualityCheck.checkManifestMetadata(
                        Optional.empty(),
                        config));
                case GENERATED_SOURCES -> results.addAll(generatedSourceQualityCheck.check(
                        Optional.empty(),
                        request.projectRoot(),
                        config));
                default -> results.add(QualityCheckCatalog.unsupportedOrSkipped(requestedCheck));
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
                case CACHE_INTEGRITY -> results.add(lockfileQualityCheck.checkWorkspaceCacheIntegrity(request, workspace));
                case EXECUTION_CONTEXT -> results.addAll(executionContextRunner.checkWorkspace(
                        request,
                        workspace,
                        selection,
                        members));
                case LOCKFILE -> results.add(lockfileQualityCheck.checkWorkspaceLockfile(request, workspace));
                case PROJECT_MODEL -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(projectModelQualityCheck.check(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                case DEPENDENCY_METADATA -> results.addAll(dependencyQualityCheck.checkWorkspaceMetadata(workspace, selection, members));
                case DEPENDENCY_POLICY -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(dependencyQualityCheck.checkPolicy(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config(),
                                workspace.root().resolve("zolt.lock"),
                                true));
                    }
                }
                case PACKAGE_METADATA -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.add(packageQualityCheck.checkMetadata(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                case PACKAGE_CONTENTS -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(packageQualityCheck.checkContents(
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
                        results.add(packageQualityCheck.checkManifestMetadata(
                                Optional.of(member.path()),
                                member.config()));
                    }
                }
                case GENERATED_SOURCES -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.addAll(generatedSourceQualityCheck.check(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                default -> results.add(QualityCheckCatalog.unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return Collections.unmodifiableMap(members);
    }
}
