package com.zolt.workspace;

import com.zolt.build.JavaRunResult;
import com.zolt.build.JavaRunner;
import com.zolt.build.RunException;
import com.zolt.build.RunResult;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.resolve.Classpath;
import com.zolt.resolve.ResolveException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class WorkspaceRunService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceBuildService workspaceBuildService;
    private final WorkspaceMemberSelector memberSelector;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;

    public WorkspaceRunService() {
        this(new JdkDetector());
    }

    WorkspaceRunService(JdkChecker jdkDetector) {
        this(
                new WorkspaceDiscoveryService(),
                new WorkspaceBuildService(jdkDetector),
                new WorkspaceMemberSelector(),
                jdkDetector,
                new JavaRunner());
    }

    WorkspaceRunService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceBuildService workspaceBuildService,
            WorkspaceMemberSelector memberSelector,
            JdkChecker jdkDetector,
            JavaRunner javaRunner) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceBuildService = workspaceBuildService;
        this.memberSelector = memberSelector;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
    }

    public WorkspaceRunResult run(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            List<String> arguments,
            Consumer<String> outputConsumer) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new ResolveException(
                "Could not find zolt-workspace.toml. Run `zolt run --workspace` from a workspace directory or create zolt-workspace.toml."));
        WorkspaceSelection selection = memberSelector.select(workspace, selectionRequest);
        WorkspaceBuildResult buildResult = workspaceBuildService.build(start, cacheRoot, false, selectionRequest);

        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(buildResult);

        List<WorkspaceRunResult.MemberRunResult> results = new ArrayList<>();
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPath);
            String mainClass = member.config().project().main().orElseThrow(() -> new RunException(
                    "Workspace member `"
                            + member.path()
                            + "` has no main class configured. Add [project].main to its zolt.toml or choose an application member."));
            JdkStatus jdkStatus = jdkDetector.detect(member.config().project().java());
            if (!jdkStatus.ok()) {
                throw new RunException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
            }
            List<Path> runtimeEntries = new ArrayList<>();
            runtimeEntries.add(memberBuild.result().outputDirectory());
            runtimeEntries.addAll(memberBuild.classpaths().runtime().entries());
            JavaRunResult javaRunResult = javaRunner.run(
                    jdkStatus.java().orElseThrow(),
                    new Classpath(runtimeEntries),
                    mainClass,
                    arguments,
                    outputConsumer);
            results.add(new WorkspaceRunResult.MemberRunResult(
                    member.path(),
                    new RunResult(memberBuild.result(), javaRunResult)));
        }
        return new WorkspaceRunResult(buildResult.resolveResult(), buildResult.members(), results);
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
