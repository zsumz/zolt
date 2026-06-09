package com.zolt.workspace;

import com.zolt.build.JavaRunResult;
import com.zolt.build.JavaRunner;
import com.zolt.build.RunPackageException;
import com.zolt.build.RunPackageResult;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.PackageMode;
import com.zolt.resolve.Classpath;
import com.zolt.resolve.ResolveException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorkspaceRunPackageService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspacePackageService workspacePackageService;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;

    public WorkspaceRunPackageService() {
        this(new JdkDetector());
    }

    WorkspaceRunPackageService(JdkChecker jdkDetector) {
        this(
                new WorkspaceDiscoveryService(),
                new WorkspacePackageService(jdkDetector),
                jdkDetector,
                new JavaRunner());
    }

    WorkspaceRunPackageService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspacePackageService workspacePackageService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspacePackageService = workspacePackageService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
    }

    public WorkspaceRunPackageResult runPackages(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            List<String> arguments) {
        return runPackages(startDirectory, cacheRoot, selectionRequest, arguments, Optional.empty());
    }

    public WorkspaceRunPackageResult runPackages(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            List<String> arguments,
            Optional<PackageMode> packageModeOverride) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new ResolveException(
                "Could not find zolt-workspace.toml. Run `zolt run-package --workspace` from a workspace directory or create zolt-workspace.toml."));
        WorkspacePackageResult packageResult = workspacePackageService.packageJars(
                start,
                cacheRoot,
                selectionRequest,
                packageModeOverride);

        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(packageResult);

        List<WorkspaceRunPackageResult.MemberRunPackageResult> results = new ArrayList<>();
        for (WorkspacePackageResult.MemberPackageResult memberPackage : packageResult.members()) {
            WorkspaceMember member = membersByPath.get(memberPackage.member());
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPackage.member());
            String mainClass = member.config().project().main().orElseThrow(() -> new RunPackageException(
                    "Workspace member `"
                            + member.path()
                            + "` has no main class configured. Add [project].main to its zolt.toml or choose an application member."));
            JdkStatus jdkStatus = jdkDetector.detect(member.config().project().java());
            if (!jdkStatus.ok()) {
                throw new RunPackageException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
            }
            if (memberPackage.result().mode() == PackageMode.SPRING_BOOT) {
                JavaRunResult javaRunResult = javaRunner.runJar(
                        jdkStatus.java().orElseThrow(),
                        memberPackage.result().jarPath(),
                        mainClass,
                        arguments);
                results.add(new WorkspaceRunPackageResult.MemberRunPackageResult(
                        memberPackage.member(),
                        new RunPackageResult(memberPackage.result(), javaRunResult)));
                continue;
            }

            List<Path> runtimeEntries = new ArrayList<>();
            runtimeEntries.add(memberPackage.result().jarPath());
            runtimeEntries.addAll(memberBuild.classpaths().runtime().entries());
            JavaRunResult javaRunResult = javaRunner.run(
                    jdkStatus.java().orElseThrow(),
                    new Classpath(runtimeEntries),
                    mainClass,
                    arguments);
            results.add(new WorkspaceRunPackageResult.MemberRunPackageResult(
                    memberPackage.member(),
                    new RunPackageResult(memberPackage.result(), javaRunResult)));
        }
        return new WorkspaceRunPackageResult(packageResult.resolveResult(), packageResult.builtMembers(), results);
    }

    private static Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath(WorkspacePackageResult result) {
        Map<String, WorkspaceBuildResult.MemberBuildResult> builds = new LinkedHashMap<>();
        for (WorkspaceBuildResult.MemberBuildResult member : result.builtMembers()) {
            builds.put(member.member(), member);
        }
        return builds;
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }
}
