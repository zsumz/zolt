package com.zolt.workspace.clean;

import com.zolt.build.CleanException;
import com.zolt.build.clean.CleanResult;
import com.zolt.build.clean.CleanService;
import com.zolt.workspace.service.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.service.WorkspaceMember;
import com.zolt.workspace.service.WorkspaceMemberSelector;
import com.zolt.workspace.service.WorkspaceSelection;
import com.zolt.workspace.service.WorkspaceSelectionRequest;
import com.zolt.workspace.discovery.WorkspaceDiscoveryService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspaceCleanService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceMemberSelector memberSelector;
    private final CleanService cleanService;

    public WorkspaceCleanService() {
        this(new WorkspaceDiscoveryService(), new WorkspaceMemberSelector(), new CleanService());
    }

    WorkspaceCleanService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceMemberSelector memberSelector,
            CleanService cleanService) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.memberSelector = memberSelector;
        this.cleanService = cleanService;
    }

    public WorkspaceCleanResult clean(Path startDirectory, WorkspaceSelectionRequest selectionRequest) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new WorkspaceConfigException(
                "Could not find workspace config. Run `zolt clean --workspace` from a workspace directory or add zolt.toml with [workspace]."));
        WorkspaceSelection selection = memberSelector.select(workspace, selectionRequest);
        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        List<WorkspaceCleanResult.MemberCleanResult> results = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            try {
                CleanResult result = cleanService.clean(member.directory(), member.config());
                results.add(new WorkspaceCleanResult.MemberCleanResult(member.path(), result));
            } catch (CleanException exception) {
                throw new CleanException(
                        "Workspace member `"
                                + member.path()
                                + "` could not be cleaned. "
                                + exception.getMessage(),
                        exception);
            }
        }
        return new WorkspaceCleanResult(selection, results);
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }
}
