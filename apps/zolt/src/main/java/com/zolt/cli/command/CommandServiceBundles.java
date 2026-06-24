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
        ZoltTomlParser tomlParser,
        ZoltTomlWriter tomlWriter,
        ResolveService resolveService) {
    CommandVersionAliasServices {
        Objects.requireNonNull(tomlParser, "tomlParser");
        Objects.requireNonNull(tomlWriter, "tomlWriter");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}

record CommandDependencyEditServices(
        CoordinateParser coordinateParser,
        ZoltTomlParser tomlParser,
        ZoltTomlWriter tomlWriter,
        ResolveService resolveService) {
    CommandDependencyEditServices {
        Objects.requireNonNull(coordinateParser, "coordinateParser");
        Objects.requireNonNull(tomlParser, "tomlParser");
        Objects.requireNonNull(tomlWriter, "tomlWriter");
        Objects.requireNonNull(resolveService, "resolveService");
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

record CommandPackageFrameworkServices(
        FrameworkPackageAugmenter packageAugmenter,
        PackagePlanService packagePlanService) {
    CommandPackageFrameworkServices {
        Objects.requireNonNull(packageAugmenter, "packageAugmenter");
        Objects.requireNonNull(packagePlanService, "packagePlanService");
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

record CommandTestFrameworkServices(
        FrameworkTestRunner frameworkTestRunner,
        ResolveService resolveService) {
    CommandTestFrameworkServices {
        Objects.requireNonNull(frameworkTestRunner, "frameworkTestRunner");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}
