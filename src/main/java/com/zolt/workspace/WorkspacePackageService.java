package com.zolt.workspace;

import com.zolt.build.PackageService;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorkspacePackageService {
    private final WorkspaceBuildService workspaceBuildService;
    private final PackageService packageService;

    public WorkspacePackageService() {
        this(
                new WorkspaceBuildService(),
                new PackageService());
    }

    WorkspacePackageService(
            WorkspaceBuildService workspaceBuildService,
            PackageService packageService) {
        this.workspaceBuildService = workspaceBuildService;
        this.packageService = packageService;
    }

    public WorkspacePackageResult packageJars(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        return packageJars(startDirectory, cacheRoot, selectionRequest, Optional.empty());
    }

    public WorkspacePackageResult packageJars(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            Optional<PackageMode> packageModeOverride) {
        WorkspaceBuildPlan plan = planPackages(startDirectory, cacheRoot, selectionRequest);
        WorkspaceBuildResult buildResult = buildPackageInputs(plan, cacheRoot);
        return packageBuiltJars(plan, buildResult, packageModeOverride);
    }

    public WorkspaceBuildPlan planPackages(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        return workspaceBuildService.planBuild(startDirectory, cacheRoot, false, selectionRequest);
    }

    public WorkspaceBuildResult buildPackageInputs(WorkspaceBuildPlan plan, Path cacheRoot) {
        return workspaceBuildService.build(plan, cacheRoot);
    }

    public WorkspacePackageResult packageBuiltJars(
            WorkspaceBuildPlan plan,
            WorkspaceBuildResult buildResult,
            Optional<PackageMode> packageModeOverride) {
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(buildResult);
        List<WorkspacePackageResult.MemberPackageResult> results = new ArrayList<>();
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPath);
            ProjectConfig memberConfig = packageModeOverride
                    .map(mode -> member.config().withPackageSettings(new PackageSettings(mode)))
                    .orElse(member.config());
            results.add(new WorkspacePackageResult.MemberPackageResult(
                    member.path(),
                    packageService.packageJar(
                            member.directory(),
                            memberConfig,
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
