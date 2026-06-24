package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;
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
    void configEditServicesRequiresEveryCollaborator() {
        CommandConfigEditServices services = CommandFrameworkServices.configEditServices();

        assertRejectsNullCollaborators(
                () -> new CommandConfigEditServices(
                        null,
                        services.tomlWriter(),
                        services.resolveService()),
                () -> new CommandConfigEditServices(
                        services.tomlParser(),
                        null,
                        services.resolveService()),
                () -> new CommandConfigEditServices(
                        services.tomlParser(),
                        services.tomlWriter(),
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
    void buildFrameworkServicesRequiresEveryCollaborator() {
        CommandBuildFrameworkServices services = CommandFrameworkServices.buildFrameworkServices();

        assertRejectsNullCollaborators(
                () -> new CommandBuildFrameworkServices(
                        null,
                        services.resolveService()),
                () -> new CommandBuildFrameworkServices(
                        services.frameworkBuildAugmenter(),
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
    void packageFrameworkServicesRequiresEveryCollaborator() {
        CommandPackageFrameworkServices services = CommandFrameworkServices.packageFrameworkServices();

        assertRejectsNullCollaborators(
                () -> new CommandPackageFrameworkServices(
                        null,
                        services.packagePlanService()),
                () -> new CommandPackageFrameworkServices(
                        services.packageAugmenter(),
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
    void runFrameworkServicesRequiresEveryCollaborator() {
        CommandRunFrameworkServices services = CommandFrameworkServices.runFrameworkServices();

        assertRejectsNullCollaborators(
                () -> new CommandRunFrameworkServices(
                        null,
                        services.resolveService()),
                () -> new CommandRunFrameworkServices(
                        services.frameworkRunAugmenter(),
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

    @Test
    void testFrameworkServicesRequiresEveryCollaborator() {
        CommandTestFrameworkServices services = CommandFrameworkServices.testFrameworkServices();

        assertRejectsNullCollaborators(
                () -> new CommandTestFrameworkServices(
                        null,
                        services.resolveService()),
                () -> new CommandTestFrameworkServices(
                        services.frameworkTestRunner(),
                        null));
    }

    @SafeVarargs
    private static void assertRejectsNullCollaborators(Supplier<Object>... factories) {
        for (Supplier<Object> factory : factories) {
            assertThrows(NullPointerException.class, factory::get);
        }
    }
}
