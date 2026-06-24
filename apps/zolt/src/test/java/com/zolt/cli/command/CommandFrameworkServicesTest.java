package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class CommandFrameworkServicesTest {
    @Test
    void coverageCommandServicesOwnsDefaultCoverageWiring() {
        CommandCoverageServices services = CommandFrameworkServices.coverageCommandServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.coverageService());
        assertNotNull(services.workspaceCoverageService());
    }

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
    void nativeCommandServicesOwnsDefaultNativeWiring() {
        CommandNativeServices services = CommandFrameworkServices.nativeCommandServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.nativeBuildService());
        assertNotNull(services.workspaceNativeBuildService());
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
    void versionAliasCommandServicesOwnsDefaultVersionAliasWiring() {
        CommandVersionAliasServices services = CommandFrameworkServices.versionAliasCommandServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.tomlWriter());
        assertNotNull(services.resolveService());
    }

    @Test
    void versionAliasCommandServicesRequiresEveryCollaborator() {
        CommandVersionAliasServices services = CommandFrameworkServices.versionAliasCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandVersionAliasServices(
                        null,
                        services.tomlWriter(),
                        services.resolveService()),
                () -> new CommandVersionAliasServices(
                        services.tomlParser(),
                        null,
                        services.resolveService()),
                () -> new CommandVersionAliasServices(
                        services.tomlParser(),
                        services.tomlWriter(),
                        null));
    }

    @Test
    void dependencyEditCommandServicesOwnsDefaultDependencyEditWiring() {
        CommandDependencyEditServices services = CommandFrameworkServices.dependencyEditCommandServices();

        assertNotNull(services.coordinateParser());
        assertNotNull(services.tomlParser());
        assertNotNull(services.tomlWriter());
        assertNotNull(services.resolveService());
    }

    @Test
    void dependencyEditCommandServicesRequiresEveryCollaborator() {
        CommandDependencyEditServices services = CommandFrameworkServices.dependencyEditCommandServices();

        assertRejectsNullCollaborators(
                () -> new CommandDependencyEditServices(
                        null,
                        services.tomlParser(),
                        services.tomlWriter(),
                        services.resolveService()),
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        null,
                        services.tomlWriter(),
                        services.resolveService()),
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        services.tomlParser(),
                        null,
                        services.resolveService()),
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        services.tomlParser(),
                        services.tomlWriter(),
                        null));
    }

    @Test
    void resolveCommandServicesOwnsDefaultResolveWiring() {
        CommandResolveServices services = CommandFrameworkServices.resolveCommandServices();

        assertNotNull(services.resolveService());
        assertNotNull(services.workspaceResolveService());
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
    void buildCommandServicesOwnsDefaultBuildWiring() {
        CommandBuildServices services = CommandFrameworkServices.buildCommandServices();

        assertNotNull(services.buildService());
        assertNotNull(services.workspaceBuildService());
        assertNotNull(services.frameworkBuildAugmenter());
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
    void packageCommandServicesOwnsDefaultPackageWiring() {
        CommandPackageServices services = CommandFrameworkServices.packageCommandServices();

        assertNotNull(services.packagePlanService());
        assertNotNull(services.packageService());
        assertNotNull(services.buildService());
        assertNotNull(services.workspacePackageService());
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
    void runPackageCommandServicesOwnsDefaultRunPackageWiring() {
        CommandRunPackageServices services = CommandFrameworkServices.runPackageCommandServices();

        assertNotNull(services.runPackageService());
        assertNotNull(services.workspaceRunPackageService());
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
    void runCommandServicesOwnsDefaultRunWiring() {
        CommandRunServices services = CommandFrameworkServices.runCommandServices();

        assertNotNull(services.runService());
        assertNotNull(services.workspaceRunService());
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
    void testCommandServicesOwnsDefaultTestWiring() {
        CommandTestServices services = CommandFrameworkServices.testCommandServices();

        assertNotNull(services.testRunService());
        assertNotNull(services.workspaceTestService());
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

    @SafeVarargs
    private static void assertRejectsNullCollaborators(Supplier<Object>... factories) {
        for (Supplier<Object> factory : factories) {
            assertThrows(NullPointerException.class, factory::get);
        }
    }
}
