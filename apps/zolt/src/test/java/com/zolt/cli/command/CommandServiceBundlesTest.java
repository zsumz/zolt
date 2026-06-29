package com.zolt.cli.command;

import static com.zolt.cli.command.CommandServiceNullGuardAssertions.assertRejectsNullCollaborators;

import com.zolt.cli.command.CommandServiceBundles.CommandBuildServices;
import com.zolt.cli.command.CommandServiceBundles.CommandCoverageServices;
import com.zolt.cli.command.CommandServiceBundles.CommandDependencyEditServices;
import com.zolt.cli.command.CommandServiceBundles.CommandNativeServices;
import com.zolt.cli.command.CommandServiceBundles.CommandPackageServices;
import com.zolt.cli.command.CommandServiceBundles.CommandResolveServices;
import com.zolt.cli.command.CommandServiceBundles.CommandRunPackageServices;
import com.zolt.cli.command.CommandServiceBundles.CommandRunServices;
import com.zolt.cli.command.CommandServiceBundles.CommandTestServices;
import com.zolt.cli.command.CommandServiceBundles.CommandVersionAliasServices;
import org.junit.jupiter.api.Test;

final class CommandServiceBundlesTest {
    @Test
    void coverageCommandServicesRequiresEveryCollaborator() {
        CommandCoverageServices services = CommandFrameworkServices.coverageCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandCoverageServices(
                        null,
                        services.coverageService(),
                        services.workspaceCoverageService()),
                () -> new CommandCoverageServices(
                        services.tomlParser(),
                        null,
                        services.workspaceCoverageService()),
                () -> new CommandCoverageServices(
                        services.tomlParser(),
                        services.coverageService(),
                        null));
    }

    @Test
    void nativeCommandServicesRequiresEveryCollaborator() {
        CommandNativeServices services = CommandFrameworkServices.nativeCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandNativeServices(
                        null,
                        services.nativeBuildService(),
                        services.workspaceNativeBuildService()),
                () -> new CommandNativeServices(
                        services.tomlParser(),
                        null,
                        services.workspaceNativeBuildService()),
                () -> new CommandNativeServices(
                        services.tomlParser(),
                        services.nativeBuildService(),
                        null));
    }

    @Test
    void versionAliasCommandServicesRequiresEveryCollaborator() {
        CommandVersionAliasServices services = CommandFrameworkServices.versionAliasCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandVersionAliasServices(
                        null));
    }

    @Test
    void dependencyEditCommandServicesRequiresEveryCollaborator() {
        CommandDependencyEditServices services = CommandFrameworkServices.dependencyEditCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandDependencyEditServices(
                        null,
                        services.configEditServices()),
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        null));
    }

    @Test
    void resolveCommandServicesRequiresEveryCollaborator() {
        CommandResolveServices services = CommandFrameworkServices.resolveCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandResolveServices(
                        null,
                        services.workspaceResolveService()),
                () -> new CommandResolveServices(
                        services.resolveService(),
                        null));
    }

    @Test
    void buildCommandServicesRequiresEveryCollaborator() {
        CommandBuildServices services = CommandFrameworkServices.buildCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandBuildServices(
                        null,
                        services.workspaceBuildService(),
                        services.frameworkBuildAugmenter()),
                () -> new CommandBuildServices(
                        services.buildService(),
                        null,
                        services.frameworkBuildAugmenter()),
                () -> new CommandBuildServices(
                        services.buildService(),
                        services.workspaceBuildService(),
                        null));
    }

    @Test
    void packageCommandServicesRequiresEveryCollaborator() {
        CommandPackageServices services = CommandFrameworkServices.packageCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandPackageServices(
                        null,
                        services.packageService(),
                        services.buildService(),
                        services.workspacePackageService()),
                () -> new CommandPackageServices(
                        services.packagePlanService(),
                        null,
                        services.buildService(),
                        services.workspacePackageService()),
                () -> new CommandPackageServices(
                        services.packagePlanService(),
                        services.packageService(),
                        null,
                        services.workspacePackageService()),
                () -> new CommandPackageServices(
                        services.packagePlanService(),
                        services.packageService(),
                        services.buildService(),
                        null));
    }

    @Test
    void runPackageCommandServicesRequiresEveryCollaborator() {
        CommandRunPackageServices services = CommandFrameworkServices.runPackageCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandRunPackageServices(
                        null,
                        services.workspaceRunPackageService()),
                () -> new CommandRunPackageServices(
                        services.runPackageService(),
                        null));
    }

    @Test
    void runCommandServicesRequiresEveryCollaborator() {
        CommandRunServices services = CommandFrameworkServices.runCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandRunServices(
                        null,
                        services.workspaceRunService()),
                () -> new CommandRunServices(
                        services.runService(),
                        null));
    }

    @Test
    void testCommandServicesRequiresEveryCollaborator() {
        CommandTestServices services = CommandFrameworkServices.testCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandTestServices(
                        null,
                        services.workspaceTestService()),
                () -> new CommandTestServices(
                        services.testRunService(),
                        null));
    }

}
