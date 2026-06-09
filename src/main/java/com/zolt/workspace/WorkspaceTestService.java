package com.zolt.workspace;

import com.zolt.build.TestRunService;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspaceTestService {
    private final WorkspaceBuildService workspaceBuildService;
    private final WorkspaceClasspathService workspaceClasspathService;
    private final TestRunService testRunService;

    public WorkspaceTestService() {
        this(
                new WorkspaceBuildService(),
                new WorkspaceClasspathService(),
                new TestRunService());
    }

    WorkspaceTestService(
            WorkspaceBuildService workspaceBuildService,
            WorkspaceClasspathService workspaceClasspathService,
            TestRunService testRunService) {
        this.workspaceBuildService = workspaceBuildService;
        this.workspaceClasspathService = workspaceClasspathService;
        this.testRunService = testRunService;
    }

    public WorkspaceTestResult test(Path startDirectory, Path cacheRoot) {
        return test(startDirectory, cacheRoot, WorkspaceSelectionRequest.defaults());
    }

    public WorkspaceTestResult test(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        WorkspaceBuildPlan plan = planTests(startDirectory, cacheRoot, selectionRequest);
        WorkspaceBuildResult buildResult = buildTestInputs(plan, cacheRoot);
        return runTests(plan, buildResult, cacheRoot);
    }

    public WorkspaceBuildPlan planTests(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        return workspaceBuildService.planBuild(startDirectory, cacheRoot, false, selectionRequest);
    }

    public WorkspaceBuildResult buildTestInputs(WorkspaceBuildPlan plan, Path cacheRoot) {
        return workspaceBuildService.build(plan, cacheRoot);
    }

    public WorkspaceTestResult runTests(
            WorkspaceBuildPlan plan,
            WorkspaceBuildResult buildResult,
            Path cacheRoot) {
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
        ZoltLockfile lockfile = plan.lockfile();
        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(buildResult);
        List<WorkspaceTestResult.MemberTestRunResult> results = new ArrayList<>();
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPath);
            ClasspathSet classpaths = workspaceClasspathService.classpathsFor(
                    workspace,
                    lockfile,
                    cacheRoot,
                    member.path());
            results.add(new WorkspaceTestResult.MemberTestRunResult(
                    member.path(),
                    testRunService.runTests(
                            member.directory(),
                            member.config(),
                            classpaths,
                            memberBuild.result())));
        }
        return new WorkspaceTestResult(buildResult.resolveResult(), buildResult.members(), results);
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
