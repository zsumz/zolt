package com.zolt.workspace;

import com.zolt.build.TestRunService;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.resolve.ResolveException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspaceTestService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceBuildService workspaceBuildService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final TestRunService testRunService;
    private final WorkspaceMemberSelector memberSelector;

    public WorkspaceTestService() {
        this(
                new WorkspaceDiscoveryService(),
                new WorkspaceBuildService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new TestRunService(),
                new WorkspaceMemberSelector());
    }

    WorkspaceTestService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceBuildService workspaceBuildService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            TestRunService testRunService,
            WorkspaceMemberSelector memberSelector) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceBuildService = workspaceBuildService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.testRunService = testRunService;
        this.memberSelector = memberSelector;
    }

    public WorkspaceTestResult test(Path startDirectory, Path cacheRoot) {
        return test(startDirectory, cacheRoot, WorkspaceSelectionRequest.defaults());
    }

    public WorkspaceTestResult test(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new ResolveException(
                "Could not find zolt-workspace.toml. Run `zolt test --workspace` from a workspace directory or create zolt-workspace.toml."));
        WorkspaceSelection selection = memberSelector.select(workspace, selectionRequest);
        WorkspaceBuildResult buildResult = workspaceBuildService.build(start, cacheRoot, false, selectionRequest);

        ZoltLockfile lockfile = lockfileReader.read(workspace.root().resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(
                lockfile,
                cacheRoot,
                workspace.root()));

        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(buildResult);
        List<WorkspaceTestResult.MemberTestRunResult> results = new ArrayList<>();
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPath);
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
