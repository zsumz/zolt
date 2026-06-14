package com.zolt.cli.command;

import com.zolt.build.BuildService;
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
import com.zolt.quarkus.QuarkusRunAugmenter;
import com.zolt.resolve.ResolveService;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspacePackageService;
import com.zolt.workspace.WorkspaceRunPackageService;
import com.zolt.workspace.WorkspaceRunService;
import com.zolt.workspace.WorkspaceTestService;
import com.zolt.workspace.WorkspaceResolveService;

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

    static RunService runService() {
        return new RunService(new QuarkusRunAugmenter());
    }

    static PackageService packageService() {
        return packageService(new QuarkusPackageAugmenter());
    }

    static PackageService packageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new PackageService(resolveService(), frameworkPackageAugmenter);
    }

    static RunPackageService runPackageService() {
        return runPackageService(new QuarkusPackageAugmenter());
    }

    static RunPackageService runPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new RunPackageService(resolveService(), frameworkPackageAugmenter);
    }

    static WorkspacePackageService workspacePackageService() {
        return workspacePackageService(new QuarkusPackageAugmenter());
    }

    static WorkspacePackageService workspacePackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new WorkspacePackageService(resolveService(), frameworkPackageAugmenter);
    }

    static WorkspaceRunPackageService workspaceRunPackageService() {
        return workspaceRunPackageService(new QuarkusPackageAugmenter());
    }

    static WorkspaceRunPackageService workspaceRunPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        return new WorkspaceRunPackageService(resolveService(), frameworkPackageAugmenter);
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
