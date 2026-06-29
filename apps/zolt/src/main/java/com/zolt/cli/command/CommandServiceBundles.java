package com.zolt.cli.command;

import com.zolt.build.BuildService;
import com.zolt.build.CoverageService;
import com.zolt.build.nativeimage.NativeBuildService;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackageService;
import com.zolt.build.RunPackageService;
import com.zolt.build.RunService;
import com.zolt.build.testruntime.TestRunService;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.maven.CoordinateParser;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceCoverageService;
import com.zolt.workspace.WorkspaceNativeBuildService;
import com.zolt.workspace.WorkspacePackageService;
import com.zolt.workspace.WorkspaceResolveService;
import com.zolt.workspace.WorkspaceRunPackageService;
import com.zolt.workspace.WorkspaceRunService;
import com.zolt.workspace.WorkspaceTestService;
import java.util.Objects;

record CommandCoverageServices(
        ZoltTomlParser tomlParser,
        CoverageService coverageService,
        WorkspaceCoverageService workspaceCoverageService) {
    CommandCoverageServices {
        Objects.requireNonNull(tomlParser, "tomlParser");
        Objects.requireNonNull(coverageService, "coverageService");
        Objects.requireNonNull(workspaceCoverageService, "workspaceCoverageService");
    }
}

record CommandNativeServices(
        ZoltTomlParser tomlParser,
        NativeBuildService nativeBuildService,
        WorkspaceNativeBuildService workspaceNativeBuildService) {
    CommandNativeServices {
        Objects.requireNonNull(tomlParser, "tomlParser");
        Objects.requireNonNull(nativeBuildService, "nativeBuildService");
        Objects.requireNonNull(workspaceNativeBuildService, "workspaceNativeBuildService");
    }
}

record CommandVersionAliasServices(
        CommandConfigEditServices configEditServices) {
    CommandVersionAliasServices {
        Objects.requireNonNull(configEditServices, "configEditServices");
    }

    ZoltTomlParser tomlParser() {
        return configEditServices.tomlParser();
    }

    ZoltTomlWriter tomlWriter() {
        return configEditServices.tomlWriter();
    }

    ResolveService resolveService() {
        return configEditServices.resolveService();
    }
}

record CommandDependencyEditServices(
        CoordinateParser coordinateParser,
        CommandConfigEditServices configEditServices) {
    CommandDependencyEditServices {
        Objects.requireNonNull(coordinateParser, "coordinateParser");
        Objects.requireNonNull(configEditServices, "configEditServices");
    }

    ZoltTomlParser tomlParser() {
        return configEditServices.tomlParser();
    }

    ZoltTomlWriter tomlWriter() {
        return configEditServices.tomlWriter();
    }

    ResolveService resolveService() {
        return configEditServices.resolveService();
    }
}

record CommandResolveServices(
        ResolveService resolveService,
        WorkspaceResolveService workspaceResolveService) {
    CommandResolveServices {
        Objects.requireNonNull(resolveService, "resolveService");
        Objects.requireNonNull(workspaceResolveService, "workspaceResolveService");
    }
}

record CommandBuildServices(
        BuildService buildService,
        WorkspaceBuildService workspaceBuildService,
        FrameworkBuildAugmenter frameworkBuildAugmenter) {
    CommandBuildServices {
        Objects.requireNonNull(buildService, "buildService");
        Objects.requireNonNull(workspaceBuildService, "workspaceBuildService");
        Objects.requireNonNull(frameworkBuildAugmenter, "frameworkBuildAugmenter");
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

record CommandRunServices(
        RunService runService,
        WorkspaceRunService workspaceRunService) {
    CommandRunServices {
        Objects.requireNonNull(runService, "runService");
        Objects.requireNonNull(workspaceRunService, "workspaceRunService");
    }
}

record CommandTestServices(
        TestRunService testRunService,
        WorkspaceTestService workspaceTestService) {
    CommandTestServices {
        Objects.requireNonNull(testRunService, "testRunService");
        Objects.requireNonNull(workspaceTestService, "workspaceTestService");
    }
}
