package com.zolt.workspace;

import com.zolt.build.NativeBuildResult;
import com.zolt.build.NativeBuildService;
import com.zolt.build.NativeImageException;
import com.zolt.build.PackagePlanService;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.project.PackageMode;
import com.zolt.resolve.ResolveService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorkspaceNativeBuildService {
    private final WorkspacePackageService workspacePackageService;
    private final NativeBuildService nativeBuildService;

    public WorkspaceNativeBuildService() {
        this(new ResolveService(), FrameworkPackageAugmenter.none());
    }

    public WorkspaceNativeBuildService(ResolveService resolveService, FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    public WorkspaceNativeBuildService(
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(
                new WorkspacePackageService(resolveService, frameworkPackageAugmenter, packagePlanService),
                new NativeBuildService());
    }

    WorkspaceNativeBuildService(
            WorkspacePackageService workspacePackageService,
            NativeBuildService nativeBuildService) {
        this.workspacePackageService = workspacePackageService;
        this.nativeBuildService = nativeBuildService;
    }

    public WorkspaceNativeBuildResult buildNative(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            Path nativeImageExecutable) {
        return buildNative(
                startDirectory,
                cacheRoot,
                selectionRequest,
                nativeImageExecutable,
                () -> {
                });
    }

    public WorkspaceNativeBuildResult buildNative(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            Path nativeImageExecutable,
            Runnable progress) {
        WorkspaceBuildPlan plan = planNative(startDirectory, cacheRoot, selectionRequest);
        WorkspaceBuildResult buildResult = buildNativeInputs(plan, cacheRoot);
        WorkspacePackageResult packageResult = packageNativeInputs(plan, buildResult);
        return buildNativeImages(plan, packageResult, nativeImageExecutable, progress);
    }

    public WorkspaceBuildPlan planNative(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest) {
        return workspacePackageService.planPackages(startDirectory, cacheRoot, selectionRequest);
    }

    public WorkspaceBuildResult buildNativeInputs(WorkspaceBuildPlan plan, Path cacheRoot) {
        return workspacePackageService.buildPackageInputs(plan, cacheRoot);
    }

    public WorkspacePackageResult packageNativeInputs(
            WorkspaceBuildPlan plan,
            WorkspaceBuildResult buildResult) {
        return workspacePackageService.packageBuiltJars(
                plan,
                buildResult,
                Optional.of(PackageMode.THIN));
    }

    public WorkspaceNativeBuildResult buildNativeImages(
            WorkspaceBuildPlan plan,
            WorkspacePackageResult packageResult,
            Path nativeImageExecutable) {
        return buildNativeImages(plan, packageResult, nativeImageExecutable, () -> {
        });
    }

    public WorkspaceNativeBuildResult buildNativeImages(
            WorkspaceBuildPlan plan,
            WorkspacePackageResult packageResult,
            Path nativeImageExecutable,
            Runnable progress) {
        Map<String, WorkspaceMember> membersByPath = membersByPath(plan.workspace());
        Map<String, WorkspaceBuildResult.MemberBuildResult> buildsByPath = buildsByPath(packageResult);
        List<WorkspaceNativeBuildResult.MemberNativeBuildResult> results = new ArrayList<>();
        for (WorkspacePackageResult.MemberPackageResult memberPackage : packageResult.members()) {
            WorkspaceMember member = membersByPath.get(memberPackage.member());
            WorkspaceBuildResult.MemberBuildResult memberBuild = buildsByPath.get(memberPackage.member());
            requireMainClass(member);
            NativeBuildResult result = nativeBuildService.buildNativeImage(
                    member.directory(),
                    member.config(),
                    memberPackage.result(),
                    memberBuild.classpaths().runtime().entries(),
                    nativeImageExecutable,
                    progress);
            results.add(new WorkspaceNativeBuildResult.MemberNativeBuildResult(member.path(), result));
        }
        return new WorkspaceNativeBuildResult(
                packageResult.resolveResult(),
                packageResult.builtMembers(),
                results);
    }

    private static void requireMainClass(WorkspaceMember member) {
        if (member.config().project().main().isEmpty()) {
            throw new NativeImageException(
                    "Workspace member `"
                            + member.path()
                            + "` has no main class configured. Add [project].main to its zolt.toml or choose an application member.");
        }
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
