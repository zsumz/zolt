package com.zolt.workspace;

import com.zolt.build.PackageService;
import com.zolt.resolve.ResolveException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspacePackageService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceBuildService workspaceBuildService;
    private final WorkspaceMemberSelector memberSelector;
    private final PackageService packageService;

    public WorkspacePackageService() {
        this(
                new WorkspaceDiscoveryService(),
                new WorkspaceBuildService(),
                new WorkspaceMemberSelector(),
                new PackageService());
    }

    WorkspacePackageService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceBuildService workspaceBuildService,
            WorkspaceMemberSelector memberSelector,
            PackageService packageService) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceBuildService = workspaceBuildService;
        this.memberSelector = memberSelector;
        this.packageService = packageService;
    }

    public WorkspacePackageResult packageJars(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new ResolveException(
                "Could not find zolt-workspace.toml. Run `zolt package --workspace` from a workspace directory or create zolt-workspace.toml."));
        WorkspaceSelection selection = memberSelector.select(workspace, selectionRequest);
        WorkspaceBuildResult buildResult = workspaceBuildService.build(start, cacheRoot, false, selectionRequest);

        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(buildResult);
        List<WorkspacePackageResult.MemberPackageResult> results = new ArrayList<>();
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPath);
            results.add(new WorkspacePackageResult.MemberPackageResult(
                    member.path(),
                    packageService.packageJar(
                            member.directory(),
                            member.config(),
                            memberBuild.result())));
        }
        return new WorkspacePackageResult(buildResult.resolveResult(), buildResult.members(), results);
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }

    private static Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath(WorkspaceBuildResult result) {
        Map<String, WorkspaceBuildResult.MemberBuildResult> builds = new LinkedHashMap<>();
        for (WorkspaceBuildResult.MemberBuildResult member : result.members()) {
            builds.put(member.member(), member);
        }
        return builds;
    }
}
