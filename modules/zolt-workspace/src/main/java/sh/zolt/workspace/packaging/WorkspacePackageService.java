package sh.zolt.workspace.packaging;

import sh.zolt.build.packageplan.PackagePlanService;
import sh.zolt.build.packaging.PackageService;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.framework.FrameworkPackageAugmenter;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.provenance.BuildProvenanceSource;
import sh.zolt.resolve.ResolveService;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceBuildPlan;
import sh.zolt.workspace.service.WorkspaceBuildResult;
import sh.zolt.workspace.service.WorkspaceBuildService;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceSelection;
import sh.zolt.workspace.service.WorkspaceSelectionRequest;
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
        this(new JdkDetector());
    }

    public WorkspacePackageService(ResolveService resolveService, FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    public WorkspacePackageService(
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(new JdkDetector(), resolveService, frameworkPackageAugmenter, packagePlanService);
    }

    public WorkspacePackageService(
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService,
            BuildProvenanceSource provenanceSource) {
        this(new JdkDetector(), resolveService, frameworkPackageAugmenter, packagePlanService, provenanceSource);
    }

    WorkspacePackageService(JdkChecker jdkDetector) {
        this(jdkDetector, new ResolveService(), FrameworkPackageAugmenter.none());
    }

    WorkspacePackageService(
            JdkChecker jdkDetector,
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(jdkDetector, resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    WorkspacePackageService(
            JdkChecker jdkDetector,
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(jdkDetector, resolveService, frameworkPackageAugmenter, packagePlanService, BuildProvenanceSource.empty());
    }

    WorkspacePackageService(
            JdkChecker jdkDetector,
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService,
            BuildProvenanceSource provenanceSource) {
        this(
                new WorkspaceBuildService(jdkDetector, resolveService, provenanceSource),
                new PackageService(resolveService, frameworkPackageAugmenter, packagePlanService, provenanceSource));
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
                            memberBuild.result(),
                            memberBuild.classpaths(),
                            memberBuild.classpathPackages())));
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
