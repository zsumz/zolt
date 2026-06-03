package com.zolt.ide;

import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMember;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkspaceIdeModelService {
    private static final int SCHEMA_VERSION = 1;

    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final IdeModelService ideModelService;

    public WorkspaceIdeModelService() {
        this(new WorkspaceDiscoveryService(), new IdeModelService());
    }

    WorkspaceIdeModelService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            IdeModelService ideModelService) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.ideModelService = ideModelService;
    }

    public WorkspaceIdeModel export(Path startDirectory, Path cacheRoot, boolean checkLock, boolean offline) {
        Path start = startDirectory.toAbsolutePath().normalize();
        try {
            Optional<Workspace> discovered = workspaceDiscoveryService.discover(start);
            if (discovered.isEmpty()) {
                return missingWorkspace(start);
            }
            Workspace workspace = discovered.orElseThrow();
            return new WorkspaceIdeModel(
                    SCHEMA_VERSION,
                    workspaceInfo(workspace),
                    projectModels(workspace, cacheRoot, checkLock, offline),
                    List.of(),
                    List.of());
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
                workspace.config().defaultMembers());
    }

    private List<WorkspaceIdeModel.ProjectModel> projectModels(
            Workspace workspace,
            Path cacheRoot,
            boolean checkLock,
            boolean offline) {
        List<WorkspaceIdeModel.ProjectModel> projects = new ArrayList<>();
        for (WorkspaceMember member : workspace.members()) {
            projects.add(new WorkspaceIdeModel.ProjectModel(
                    member.path(),
                    ideModelService.export(member.directory(), cacheRoot, checkLock, offline)));
        }
        return List.copyOf(projects);
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
        return new WorkspaceIdeModel.WorkspaceInfo(null, null, null, null, List.of(), List.of());
    }
}
