package com.zolt.workspace;

import com.zolt.build.JavaRunResult;
import com.zolt.build.JavaRunner;
import com.zolt.build.RunException;
import com.zolt.build.RunResult;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.classpath.Classpath;
import com.zolt.resolve.ResolveService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class WorkspaceRunService {
    private final WorkspaceBuildService workspaceBuildService;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;

    public WorkspaceRunService() {
        this(new JdkDetector());
    }

    public WorkspaceRunService(ResolveService resolveService) {
        this(new JdkDetector(), resolveService);
    }

    WorkspaceRunService(JdkChecker jdkDetector) {
        this(jdkDetector, new ResolveService());
    }

    WorkspaceRunService(JdkChecker jdkDetector, ResolveService resolveService) {
        this(
                new WorkspaceBuildService(jdkDetector, resolveService),
                jdkDetector,
                new JavaRunner());
    }

    WorkspaceRunService(
            WorkspaceBuildService workspaceBuildService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner) {
        this.workspaceBuildService = workspaceBuildService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
    }

    public WorkspaceRunResult run(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            List<String> arguments,
            Consumer<String> outputConsumer) {
        WorkspaceBuildPlan plan = planRun(startDirectory, cacheRoot, selectionRequest);
        WorkspaceBuildResult buildResult = buildRunInputs(plan, cacheRoot);
        return runBuiltMembers(plan, buildResult, arguments, outputConsumer);
    }

    public WorkspaceBuildPlan planRun(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        return workspaceBuildService.planBuild(startDirectory, cacheRoot, false, selectionRequest);
    }

    public WorkspaceBuildResult buildRunInputs(WorkspaceBuildPlan plan, Path cacheRoot) {
        return workspaceBuildService.build(plan, cacheRoot);
    }

    public WorkspaceRunResult runBuiltMembers(
            WorkspaceBuildPlan plan,
            WorkspaceBuildResult buildResult,
            List<String> arguments,
            Consumer<String> outputConsumer) {
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
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
