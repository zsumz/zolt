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

public final class CommandServiceBundles {
    private CommandServiceBundles() {
    }

    public record CommandCoverageServices(
            ZoltTomlParser tomlParser,
            CoverageService coverageService,
            WorkspaceCoverageService workspaceCoverageService) {
        public CommandCoverageServices {
            Objects.requireNonNull(tomlParser, "tomlParser");
            Objects.requireNonNull(coverageService, "coverageService");
            Objects.requireNonNull(workspaceCoverageService, "workspaceCoverageService");
        }
    }

    public record CommandNativeServices(
            ZoltTomlParser tomlParser,
            NativeBuildService nativeBuildService,
            WorkspaceNativeBuildService workspaceNativeBuildService) {
        public CommandNativeServices {
            Objects.requireNonNull(tomlParser, "tomlParser");
            Objects.requireNonNull(nativeBuildService, "nativeBuildService");
            Objects.requireNonNull(workspaceNativeBuildService, "workspaceNativeBuildService");
        }
    }

    public record CommandVersionAliasServices(
            CommandServiceClusters.CommandConfigEditServices configEditServices) {
        public CommandVersionAliasServices {
            Objects.requireNonNull(configEditServices, "configEditServices");
        }

        public ZoltTomlParser tomlParser() {
            return configEditServices.tomlParser();
        }

        public ZoltTomlWriter tomlWriter() {
            return configEditServices.tomlWriter();
        }

        public ResolveService resolveService() {
            return configEditServices.resolveService();
        }
    }

    public record CommandDependencyEditServices(
            CoordinateParser coordinateParser,
            CommandServiceClusters.CommandConfigEditServices configEditServices) {
        public CommandDependencyEditServices {
            Objects.requireNonNull(coordinateParser, "coordinateParser");
            Objects.requireNonNull(configEditServices, "configEditServices");
        }

        public ZoltTomlParser tomlParser() {
            return configEditServices.tomlParser();
        }

        public ZoltTomlWriter tomlWriter() {
            return configEditServices.tomlWriter();
        }

        public ResolveService resolveService() {
            return configEditServices.resolveService();
        }
    }

    public record CommandResolveServices(
            ResolveService resolveService,
            WorkspaceResolveService workspaceResolveService) {
        public CommandResolveServices {
            Objects.requireNonNull(resolveService, "resolveService");
            Objects.requireNonNull(workspaceResolveService, "workspaceResolveService");
        }
    }

    public record CommandBuildServices(
            BuildService buildService,
            WorkspaceBuildService workspaceBuildService,
            FrameworkBuildAugmenter frameworkBuildAugmenter) {
        public CommandBuildServices {
            Objects.requireNonNull(buildService, "buildService");
            Objects.requireNonNull(workspaceBuildService, "workspaceBuildService");
            Objects.requireNonNull(frameworkBuildAugmenter, "frameworkBuildAugmenter");
        }
    }

    public record CommandPackageServices(
            PackagePlanService packagePlanService,
            PackageService packageService,
            BuildService buildService,
            WorkspacePackageService workspacePackageService) {
        public CommandPackageServices {
            Objects.requireNonNull(packagePlanService, "packagePlanService");
            Objects.requireNonNull(packageService, "packageService");
            Objects.requireNonNull(buildService, "buildService");
            Objects.requireNonNull(workspacePackageService, "workspacePackageService");
        }
    }

    public record CommandRunPackageServices(
            RunPackageService runPackageService,
            WorkspaceRunPackageService workspaceRunPackageService) {
        public CommandRunPackageServices {
            Objects.requireNonNull(runPackageService, "runPackageService");
            Objects.requireNonNull(workspaceRunPackageService, "workspaceRunPackageService");
        }
    }

    public record CommandRunServices(
            RunService runService,
            WorkspaceRunService workspaceRunService) {
        public CommandRunServices {
            Objects.requireNonNull(runService, "runService");
            Objects.requireNonNull(workspaceRunService, "workspaceRunService");
        }
    }

    public record CommandTestServices(
            TestRunService testRunService,
            WorkspaceTestService workspaceTestService) {
        public CommandTestServices {
            Objects.requireNonNull(testRunService, "testRunService");
            Objects.requireNonNull(workspaceTestService, "workspaceTestService");
        }
    }
}
