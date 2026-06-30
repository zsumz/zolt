package com.zolt.cli.command;

import com.zolt.build.BuildService;
import com.zolt.build.coverage.CoverageService;
import com.zolt.build.packageplan.PackagePlanService;
import com.zolt.build.packaging.PackageService;
import com.zolt.build.run.RunPackageService;
import com.zolt.build.run.RunService;
import com.zolt.build.nativeimage.NativeBuildService;
import com.zolt.build.testruntime.TestRunService;
import com.zolt.cli.command.CommandServiceBundles.*;
import com.zolt.cli.command.CommandServiceClusters.*;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.maven.CoordinateParser;
import com.zolt.quarkus.QuarkusBuildAugmenter;
import com.zolt.quarkus.QuarkusDependencyRequestPlanner;
import com.zolt.quarkus.QuarkusPackageAugmenter;
import com.zolt.quarkus.QuarkusPackagePlanRules;
import com.zolt.quarkus.QuarkusRunAugmenter;
import com.zolt.quarkus.testworker.QuarkusFrameworkTestRunner;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.coverage.WorkspaceCoverageService;
import com.zolt.workspace.packaging.WorkspaceNativeBuildService;
import com.zolt.workspace.packaging.WorkspacePackageService;
import com.zolt.workspace.resolve.WorkspaceResolveService;
import com.zolt.workspace.packaging.WorkspaceRunPackageService;
import com.zolt.workspace.run.WorkspaceRunService;
import com.zolt.workspace.WorkspaceTestService;
import java.util.List;

public final class CommandFrameworkServices {
    private CommandFrameworkServices() {}

    static ResolveService resolveService() {
        return new ResolveService(new QuarkusDependencyRequestPlanner());
    }

    static CommandConfigEditServices configEditServices() {
        return new CommandConfigEditServices(
                new ZoltTomlParser(),
                new ZoltTomlWriter(),
                resolveService());
    }

    public static CommandDependencyEditServices dependencyEditCommandServices() {
        return new CommandDependencyEditServices(
                new CoordinateParser(),
                configEditServices());
    }

    public static CommandVersionAliasServices versionAliasCommandServices() {
        return new CommandVersionAliasServices(configEditServices());
    }

    static WorkspaceResolveService workspaceResolveService() {
        return new WorkspaceResolveService(resolveService());
    }

    public static CommandResolveServices resolveCommandServices() {
        ResolveService resolveService = resolveService();
        return new CommandResolveServices(
                resolveService,
                new WorkspaceResolveService(resolveService));
    }

    static WorkspaceBuildService workspaceBuildService() {
        return workspaceBuildService(buildFrameworkServices());
    }

    static BuildService buildService() {
        return buildService(buildFrameworkServices());
    }

    static FrameworkBuildAugmenter buildAugmenter() {
        return buildFrameworkServices().frameworkBuildAugmenter();
    }

    static CommandBuildFrameworkServices buildFrameworkServices() {
        return new CommandBuildFrameworkServices(new QuarkusBuildAugmenter(), resolveService());
    }

    public static CommandBuildServices buildCommandServices() {
        CommandBuildFrameworkServices buildFrameworkServices = buildFrameworkServices();
        return new CommandBuildServices(
                buildService(buildFrameworkServices),
                workspaceBuildService(buildFrameworkServices),
                buildFrameworkServices.frameworkBuildAugmenter());
    }

    private static BuildService buildService(CommandBuildFrameworkServices buildFrameworkServices) {
        return new BuildService(buildFrameworkServices.resolveService());
    }

    private static WorkspaceBuildService workspaceBuildService(CommandBuildFrameworkServices buildFrameworkServices) {
        return new WorkspaceBuildService(buildFrameworkServices.resolveService());
    }

    static PackagePlanService packagePlanService() {
        return new PackagePlanService(List.of(new QuarkusPackagePlanRules()));
    }

    static CommandPackageFrameworkServices packageFrameworkServices() {
        return new CommandPackageFrameworkServices(new QuarkusPackageAugmenter(), packagePlanService());
    }

    public static CommandPackageServices packageCommandServices() {
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
        return runService(runFrameworkServices());
    }

    static RunService runService(FrameworkRunAugmenter frameworkRunAugmenter) {
        return runService(new CommandRunFrameworkServices(frameworkRunAugmenter, resolveService()));
    }

    static CommandRunFrameworkServices runFrameworkServices() {
        return new CommandRunFrameworkServices(new QuarkusRunAugmenter(), resolveService());
    }

    private static RunService runService(CommandRunFrameworkServices runFrameworkServices) {
        return new RunService(runFrameworkServices.frameworkRunAugmenter());
    }

    public static CommandRunServices runCommandServices() {
        CommandRunFrameworkServices runFrameworkServices = runFrameworkServices();
        return new CommandRunServices(
                runService(runFrameworkServices),
                workspaceRunService(runFrameworkServices));
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

    public static CommandRunPackageServices runPackageCommandServices() {
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

    public static CommandNativeServices nativeCommandServices() {
        return new CommandNativeServices(
                new ZoltTomlParser(),
                new NativeBuildService(),
                workspaceNativeBuildService());
    }

    public static CommandCoverageServices coverageCommandServices() {
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
        return workspaceRunService(runFrameworkServices());
    }

    private static WorkspaceRunService workspaceRunService(CommandRunFrameworkServices runFrameworkServices) {
        return new WorkspaceRunService(runFrameworkServices.resolveService());
    }

    static CommandTestFrameworkServices testFrameworkServices() {
        return new CommandTestFrameworkServices(new QuarkusFrameworkTestRunner(), resolveService());
    }

    public static CommandTestServices testCommandServices() {
        CommandTestFrameworkServices testFrameworkServices = testFrameworkServices();
        return new CommandTestServices(
                testRunService(testFrameworkServices),
                workspaceTestService(testFrameworkServices));
    }

    static TestRunService testRunService(FrameworkTestRunner frameworkTestRunner) {
        return testRunService(new CommandTestFrameworkServices(frameworkTestRunner, resolveService()));
    }

    private static TestRunService testRunService(CommandTestFrameworkServices testFrameworkServices) {
        return new TestRunService(
                testFrameworkServices.frameworkTestRunner(),
                testFrameworkServices.resolveService());
    }

    static TestRunService testRunService() {
        return testRunService(testFrameworkServices());
    }

    static WorkspaceTestService workspaceTestService(FrameworkTestRunner frameworkTestRunner) {
        return workspaceTestService(new CommandTestFrameworkServices(frameworkTestRunner, resolveService()));
    }

    private static WorkspaceTestService workspaceTestService(CommandTestFrameworkServices testFrameworkServices) {
        return new WorkspaceTestService(
                testFrameworkServices.resolveService(),
                testFrameworkServices.frameworkTestRunner());
    }

    static WorkspaceTestService workspaceTestService() {
        return workspaceTestService(testFrameworkServices());
    }
}
