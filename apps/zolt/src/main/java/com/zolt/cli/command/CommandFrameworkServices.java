package com.zolt.cli.command;

import com.zolt.build.BuildService;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackageService;
import com.zolt.build.RunPackageService;
import com.zolt.build.RunService;
import com.zolt.build.TestRunService;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.quarkus.QuarkusBuildAugmenter;
import com.zolt.quarkus.QuarkusDependencyRequestPlanner;
import com.zolt.quarkus.QuarkusFrameworkTestRunner;
import com.zolt.quarkus.QuarkusPackageAugmenter;
import com.zolt.quarkus.QuarkusPackagePlanRules;
import com.zolt.quarkus.QuarkusRunAugmenter;
import com.zolt.resolve.ResolveService;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceNativeBuildService;
import com.zolt.workspace.WorkspacePackageService;
import com.zolt.workspace.WorkspaceRunPackageService;
import com.zolt.workspace.WorkspaceRunService;
import com.zolt.workspace.WorkspaceTestService;
import com.zolt.workspace.WorkspaceResolveService;
import java.util.List;
import java.util.Objects;

final class CommandFrameworkServices {
    private CommandFrameworkServices() {}

    static ResolveService resolveService() {
        return new ResolveService(new QuarkusDependencyRequestPlanner());
    }

    static WorkspaceResolveService workspaceResolveService() {
        return new WorkspaceResolveService(resolveService());
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

    static PackagePlanService packagePlanService() {
        return new PackagePlanService(List.of(new QuarkusPackagePlanRules()));
    }

    static CommandPackageServices packageCommandServices() {
        ResolveService resolveService = resolveService();
        PackagePlanService packagePlanService = packagePlanService();
        FrameworkPackageAugmenter packageAugmenter = new QuarkusPackageAugmenter();
        return new CommandPackageServices(
                packagePlanService,
                new PackageService(resolveService, packageAugmenter, packagePlanService),
                new BuildService(resolveService),
                new WorkspacePackageService(resolveService, packageAugmenter, packagePlanService));
    }

    static RunService runService() {
        return new RunService(new QuarkusRunAugmenter());
    }

    static PackageService packageService() {
        return packageService(new QuarkusPackageAugmenter());
    }

    static PackageService packageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new PackageService(resolveService(), frameworkPackageAugmenter, packagePlanService());
    }

    static RunPackageService runPackageService() {
        return runPackageService(new QuarkusPackageAugmenter());
    }

    static CommandRunPackageServices runPackageCommandServices() {
        FrameworkPackageAugmenter packageAugmenter = new QuarkusPackageAugmenter();
        return new CommandRunPackageServices(
                runPackageService(packageAugmenter),
                workspaceRunPackageService(packageAugmenter));
    }

    static RunPackageService runPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new RunPackageService(resolveService(), frameworkPackageAugmenter, packagePlanService());
    }

    static WorkspacePackageService workspacePackageService() {
        return workspacePackageService(new QuarkusPackageAugmenter());
    }

    static WorkspacePackageService workspacePackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new WorkspacePackageService(resolveService(), frameworkPackageAugmenter, packagePlanService());
    }

    static WorkspaceNativeBuildService workspaceNativeBuildService() {
        return new WorkspaceNativeBuildService(resolveService(), new QuarkusPackageAugmenter(), packagePlanService());
    }

    static WorkspaceRunPackageService workspaceRunPackageService() {
        return workspaceRunPackageService(new QuarkusPackageAugmenter());
    }

    static WorkspaceRunPackageService workspaceRunPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new WorkspaceRunPackageService(resolveService(), frameworkPackageAugmenter, packagePlanService());
    }

    static WorkspaceRunService workspaceRunService() {
        return new WorkspaceRunService(resolveService());
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

record CommandPackageServices(
        PackagePlanService packagePlanService,
        PackageService packageService,
        BuildService buildService,
        WorkspacePackageService workspacePackageService) {
    CommandPackageServices {
        Objects.requireNonNull(packagePlanService, "packagePlanService");
        Objects.requireNonNull(packageService, "packageService");
        Objects.requireNonNull(buildService, "buildService");
        Objects.requireNonNull(workspacePackageService, "workspacePackageService");
    }
}

record CommandRunPackageServices(
        RunPackageService runPackageService,
        WorkspaceRunPackageService workspaceRunPackageService) {
    CommandRunPackageServices {
        Objects.requireNonNull(runPackageService, "runPackageService");
        Objects.requireNonNull(workspaceRunPackageService, "workspaceRunPackageService");
    }
}
