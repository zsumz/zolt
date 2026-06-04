package com.zolt.ide;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.resolve.Classpath;
import com.zolt.resolve.ResolveException;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceClasspathService;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceProjectEdge;
import com.zolt.workspace.WorkspaceResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkspaceIdeModelService {
    private static final int SCHEMA_VERSION = 1;

    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final IdeModelService ideModelService;
    private final ZoltLockfileReader lockfileReader;
    private final WorkspaceClasspathService workspaceClasspathService;
    private final WorkspaceResolveService workspaceResolveService;

    public WorkspaceIdeModelService() {
        this(
                new WorkspaceDiscoveryService(),
                new IdeModelService(),
                new ZoltLockfileReader(),
                new WorkspaceClasspathService(),
                new WorkspaceResolveService());
    }

    WorkspaceIdeModelService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            IdeModelService ideModelService,
            ZoltLockfileReader lockfileReader,
            WorkspaceClasspathService workspaceClasspathService,
            WorkspaceResolveService workspaceResolveService) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.ideModelService = ideModelService;
        this.lockfileReader = lockfileReader;
        this.workspaceClasspathService = workspaceClasspathService;
        this.workspaceResolveService = workspaceResolveService;
    }

    public WorkspaceIdeModel export(Path startDirectory, Path cacheRoot, boolean checkLock, boolean offline) {
        Path start = startDirectory.toAbsolutePath().normalize();
        try {
            Optional<Workspace> discovered = workspaceDiscoveryService.discover(start);
            if (discovered.isEmpty()) {
                return missingWorkspace(start);
            }
            Workspace workspace = discovered.orElseThrow();
            WorkspaceLockState lockState = workspaceLockState(workspace, cacheRoot, checkLock, offline);
            return new WorkspaceIdeModel(
                    SCHEMA_VERSION,
                    workspaceInfo(workspace),
                    projectModels(workspace, cacheRoot, lockState),
                    projectEdges(workspace),
                    lockState.diagnostics());
        } catch (WorkspaceConfigException exception) {
            return invalidWorkspace(start, exception);
        }
    }

    private WorkspaceIdeModel.WorkspaceInfo workspaceInfo(Workspace workspace) {
        return new WorkspaceIdeModel.WorkspaceInfo(
                workspace.config().name(),
                workspace.root(),
                workspace.configPath(),
                workspace.root().resolve("zolt.lock"),
                workspace.config().members(),
                workspace.config().defaultMembers(),
                workspace.buildOrder());
    }

    private List<WorkspaceIdeModel.ProjectModel> projectModels(
            Workspace workspace,
            Path cacheRoot,
            WorkspaceLockState lockState) {
        List<WorkspaceIdeModel.ProjectModel> projects = new ArrayList<>();
        for (WorkspaceMember member : workspace.members()) {
            projects.add(new WorkspaceIdeModel.ProjectModel(
                    member.path(),
                    ideModelService.exportWithClasspaths(
                            member.directory(),
                            workspace.root().resolve("zolt.lock"),
                            classpaths(workspace, member, cacheRoot, lockState.lockfile()),
                            List.of())));
        }
        return List.copyOf(projects);
    }

    private WorkspaceLockState workspaceLockState(
            Workspace workspace,
            Path cacheRoot,
            boolean checkLock,
            boolean offline) {
        Path lockfilePath = workspace.root().resolve("zolt.lock").normalize();
        if (!Files.exists(lockfilePath)) {
            return new WorkspaceLockState(
                    null,
                    List.of(new IdeModel.Diagnostic(
                            "error",
                            "LOCKFILE_MISSING",
                            "Could not find workspace zolt.lock.",
                            lockfilePath,
                            "Run zolt resolve --workspace.")));
        }

        try {
            ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
            List<IdeModel.Diagnostic> diagnostics = new ArrayList<>();
            if (checkLock) {
                checkWorkspaceLockFreshness(workspace, cacheRoot, offline, lockfilePath, diagnostics);
            }
            return new WorkspaceLockState(lockfile, diagnostics);
        } catch (LockfileReadException exception) {
            return new WorkspaceLockState(
                    null,
                    List.of(new IdeModel.Diagnostic(
                            "error",
                            "LOCKFILE_UNREADABLE",
                            exception.getMessage(),
                            lockfilePath,
                            "Run zolt resolve --workspace.")));
        }
    }

    private void checkWorkspaceLockFreshness(
            Workspace workspace,
            Path cacheRoot,
            boolean offline,
            Path lockfilePath,
            List<IdeModel.Diagnostic> diagnostics) {
        try {
            workspaceResolveService.resolve(workspace.root(), cacheRoot, true, offline);
        } catch (ResolveException exception) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    lockDiagnosticCode(exception),
                    exception.getMessage(),
                    lockfilePath,
                    "Run zolt resolve --workspace."));
        } catch (ArtifactCacheException exception) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    "LOCKFILE_CHECK_UNAVAILABLE",
                    exception.getMessage(),
                    lockfilePath,
                    "Run zolt resolve --workspace without --offline to seed the cache, then retry zolt ide model --workspace --check-lock --offline."));
        }
    }

    private IdeModel.ClasspathInfo classpaths(
            Workspace workspace,
            WorkspaceMember member,
            Path cacheRoot,
            ZoltLockfile lockfile) {
        if (lockfile == null) {
            return new IdeModel.ClasspathInfo(List.of(), List.of(), List.of());
        }
        ClasspathSet classpaths = workspaceClasspathService.classpathsFor(workspace, lockfile, cacheRoot, member.path());
        Path mainOutput = member.directory().resolve(member.config().build().output()).normalize();
        Path testOutput = member.directory().resolve(member.config().build().testOutput()).normalize();
        return new IdeModel.ClasspathInfo(
                absoluteEntries(classpaths.compile()),
                withOutputs(List.of(mainOutput), classpaths.runtime()),
                withOutputs(List.of(mainOutput, testOutput), classpaths.test()));
    }

    private static String lockDiagnosticCode(ResolveException exception) {
        return exception.getMessage().contains("out of date")
                ? "LOCKFILE_STALE"
                : "LOCKFILE_CHECK_FAILED";
    }

    private static List<Path> withOutputs(List<Path> outputs, Classpath classpath) {
        List<Path> entries = new ArrayList<>(outputs);
        entries.addAll(absoluteEntries(classpath));
        return entries;
    }

    private static List<Path> absoluteEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private static List<WorkspaceIdeModel.ProjectEdge> projectEdges(Workspace workspace) {
        List<WorkspaceIdeModel.ProjectEdge> edges = new ArrayList<>();
        for (WorkspaceProjectEdge edge : workspace.edges()) {
            edges.add(new WorkspaceIdeModel.ProjectEdge(
                    edge.from(),
                    edge.to(),
                    edge.scope(),
                    edge.coordinate(),
                    edge.exported()));
        }
        return List.copyOf(edges);
    }

    private static WorkspaceIdeModel missingWorkspace(Path start) {
        return new WorkspaceIdeModel(
                SCHEMA_VERSION,
                emptyWorkspaceInfo(),
                List.of(),
                List.of(),
                List.of(new IdeModel.Diagnostic(
                        "error",
                        "WORKSPACE_NOT_FOUND",
                        "Could not find zolt-workspace.toml.",
                        start,
                        "Run from a workspace directory or create zolt-workspace.toml.")));
    }

    private static WorkspaceIdeModel invalidWorkspace(Path start, WorkspaceConfigException exception) {
        return new WorkspaceIdeModel(
                SCHEMA_VERSION,
                emptyWorkspaceInfo(),
                List.of(),
                List.of(),
                List.of(new IdeModel.Diagnostic(
                        "error",
                        "WORKSPACE_INVALID",
                        exception.getMessage(),
                        start,
                        "Fix zolt-workspace.toml and run zolt ide model --workspace --format json again.")));
    }

    private static WorkspaceIdeModel.WorkspaceInfo emptyWorkspaceInfo() {
        return new WorkspaceIdeModel.WorkspaceInfo(null, null, null, null, List.of(), List.of(), List.of());
    }

    private record WorkspaceLockState(ZoltLockfile lockfile, List<IdeModel.Diagnostic> diagnostics) {
        private WorkspaceLockState {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
