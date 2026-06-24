package com.zolt.cli.command;

import com.zolt.build.BuildService;
import com.zolt.build.CoverageService;
import com.zolt.build.NativeBuildService;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackageService;
import com.zolt.build.RunPackageService;
import com.zolt.build.RunService;
import com.zolt.build.TestRunService;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.maven.CoordinateParser;
import com.zolt.quarkus.QuarkusBuildAugmenter;
import com.zolt.quarkus.QuarkusDependencyRequestPlanner;
import com.zolt.quarkus.QuarkusFrameworkTestRunner;
import com.zolt.quarkus.QuarkusPackageAugmenter;
import com.zolt.quarkus.QuarkusPackagePlanRules;
import com.zolt.quarkus.QuarkusRunAugmenter;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceCoverageService;
import com.zolt.workspace.WorkspaceNativeBuildService;
import com.zolt.workspace.WorkspacePackageService;
import com.zolt.workspace.WorkspaceRunPackageService;
import com.zolt.workspace.WorkspaceRunService;
import com.zolt.workspace.WorkspaceTestService;
import com.zolt.workspace.WorkspaceResolveService;
import java.util.List;

final class CommandFrameworkServices {
    private CommandFrameworkServices() {}

    static ResolveService resolveService() {
        return new ResolveService(new QuarkusDependencyRequestPlanner());
    }

    static CommandDependencyEditServices dependencyEditCommandServices() {
        return new CommandDependencyEditServices(
                new CoordinateParser(),
                new ZoltTomlParser(),
                new ZoltTomlWriter(),
                resolveService());
    }

    static CommandVersionAliasServices versionAliasCommandServices() {
        return new CommandVersionAliasServices(
                new ZoltTomlParser(),
                new ZoltTomlWriter(),
                resolveService());
    }

    static WorkspaceResolveService workspaceResolveService() {
        return new WorkspaceResolveService(resolveService());
    }

    static CommandResolveServices resolveCommandServices() {
        ResolveService resolveService = resolveService();
        return new CommandResolveServices(
                resolveService,
                new WorkspaceResolveService(resolveService));
    }

    static WorkspaceBuildService workspaceBuildService() {
        return new WorkspaceBuildService(resolveService());
    }

    static BuildService buildService() {
        return new BuildService(resolveService());
    }

    static FrameworkBuildAugmenter buildAugmenter() {
        return new QuarkusBuildAugmenter();
    }

    static CommandBuildServices buildCommandServices() {
        ResolveService resolveService = resolveService();
        return new CommandBuildServices(
                new BuildService(resolveService),
                new WorkspaceBuildService(resolveService),
                buildAugmenter());
    }

    static PackagePlanService packagePlanService() {
        return new PackagePlanService(List.of(new QuarkusPackagePlanRules()));
    }

    static CommandPackageFrameworkServices packageFrameworkServices() {
        return new CommandPackageFrameworkServices(new QuarkusPackageAugmenter(), packagePlanService());
    }

    static CommandPackageServices packageCommandServices() {
        ResolveService resolveService = resolveService();
        CommandPackageFrameworkServices packageFrameworkServices = packageFrameworkServices();
        PackagePlanService packagePlanService = packageFrameworkServices.packagePlanService();
        FrameworkPackageAugmenter packageAugmenter = packageFrameworkServices.packageAugmenter();
        return new CommandPackageServices(
                packagePlanService,
                new PackageService(resolveService, packageAugmenter, packagePlanService),
                new BuildService(resolveService),
                new WorkspacePackageService(resolveService, packageAugmenter, packagePlanService));
    }

    static RunService runService() {
        return new RunService(new QuarkusRunAugmenter());
    }

    static CommandRunServices runCommandServices() {
        return new CommandRunServices(
                runService(),
                workspaceRunService());
    }

    static PackageService packageService() {
        return packageService(packageFrameworkServices());
    }

    static PackageService packageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return packageService(new CommandPackageFrameworkServices(frameworkPackageAugmenter, packagePlanService()));
    }

    private static PackageService packageService(CommandPackageFrameworkServices packageFrameworkServices) {
        return new PackageService(
                resolveService(),
                packageFrameworkServices.packageAugmenter(),
                packageFrameworkServices.packagePlanService());
    }

    static RunPackageService runPackageService() {
        return runPackageService(packageFrameworkServices());
    }

    static CommandRunPackageServices runPackageCommandServices() {
        CommandPackageFrameworkServices packageFrameworkServices = packageFrameworkServices();
        return new CommandRunPackageServices(
                runPackageService(packageFrameworkServices),
                workspaceRunPackageService(packageFrameworkServices));
    }

    static RunPackageService runPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return runPackageService(new CommandPackageFrameworkServices(frameworkPackageAugmenter, packagePlanService()));
    }

    private static RunPackageService runPackageService(CommandPackageFrameworkServices packageFrameworkServices) {
        return new RunPackageService(
                resolveService(),
                packageFrameworkServices.packageAugmenter(),
                packageFrameworkServices.packagePlanService());
    }

    static WorkspacePackageService workspacePackageService() {
        return workspacePackageService(packageFrameworkServices());
    }

    static WorkspacePackageService workspacePackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return workspacePackageService(
                new CommandPackageFrameworkServices(frameworkPackageAugmenter, packagePlanService()));
    }

    private static WorkspacePackageService workspacePackageService(
            CommandPackageFrameworkServices packageFrameworkServices) {
        return new WorkspacePackageService(
                resolveService(),
                packageFrameworkServices.packageAugmenter(),
                packageFrameworkServices.packagePlanService());
    }

    static WorkspaceNativeBuildService workspaceNativeBuildService() {
        CommandPackageFrameworkServices packageFrameworkServices = packageFrameworkServices();
        return new WorkspaceNativeBuildService(
                resolveService(),
                packageFrameworkServices.packageAugmenter(),
                packageFrameworkServices.packagePlanService());
    }

    static CommandNativeServices nativeCommandServices() {
        return new CommandNativeServices(
                new ZoltTomlParser(),
                new NativeBuildService(),
                workspaceNativeBuildService());
    }

    static CommandCoverageServices coverageCommandServices() {
        return new CommandCoverageServices(
                new ZoltTomlParser(),
                new CoverageService(),
                new WorkspaceCoverageService());
    }

    static WorkspaceRunPackageService workspaceRunPackageService() {
        return workspaceRunPackageService(packageFrameworkServices());
    }

    static WorkspaceRunPackageService workspaceRunPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return workspaceRunPackageService(
                new CommandPackageFrameworkServices(frameworkPackageAugmenter, packagePlanService()));
    }

    private static WorkspaceRunPackageService workspaceRunPackageService(
            CommandPackageFrameworkServices packageFrameworkServices) {
        return new WorkspaceRunPackageService(
                resolveService(),
                packageFrameworkServices.packageAugmenter(),
                packageFrameworkServices.packagePlanService());
    }

    static WorkspaceRunService workspaceRunService() {
        return new WorkspaceRunService(resolveService());
    }

    static CommandTestServices testCommandServices() {
        FrameworkTestRunner frameworkTestRunner = new QuarkusFrameworkTestRunner();
        return new CommandTestServices(
                testRunService(frameworkTestRunner),
                workspaceTestService(frameworkTestRunner));
    }

    static TestRunService testRunService(FrameworkTestRunner frameworkTestRunner) {
        return new TestRunService(frameworkTestRunner, resolveService());
    }

    static TestRunService testRunService() {
        return testRunService(new QuarkusFrameworkTestRunner());
    }

    static WorkspaceTestService workspaceTestService(FrameworkTestRunner frameworkTestRunner) {
        return new WorkspaceTestService(resolveService(), frameworkTestRunner);
    }

    static WorkspaceTestService workspaceTestService() {
        return workspaceTestService(new QuarkusFrameworkTestRunner());
    }
}
