package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CommandFrameworkServicesTest {
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandVersionAliasServices(
                        null,
                        services.tomlWriter(),
                        services.resolveService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandVersionAliasServices(
                        services.tomlParser(),
                        null,
                        services.resolveService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandDependencyEditServices(
                        null,
                        services.tomlParser(),
                        services.tomlWriter(),
                        services.resolveService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        null,
                        services.tomlWriter(),
                        services.resolveService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandDependencyEditServices(
                        services.coordinateParser(),
                        services.tomlParser(),
                        null,
                        services.resolveService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandResolveServices(
                        null,
                        services.workspaceResolveService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandBuildServices(
                        null,
                        services.workspaceBuildService(),
                        services.frameworkBuildAugmenter()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandBuildServices(
                        services.buildService(),
                        null,
                        services.frameworkBuildAugmenter()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandPackageServices(
                        null,
                        services.packageService(),
                        services.buildService(),
                        services.workspacePackageService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandPackageServices(
                        services.packagePlanService(),
                        null,
                        services.buildService(),
                        services.workspacePackageService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandPackageServices(
                        services.packagePlanService(),
                        services.packageService(),
                        null,
                        services.workspacePackageService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandRunPackageServices(
                        null,
                        services.workspaceRunPackageService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandRunServices(
                        null,
                        services.workspaceRunService()));
        assertThrows(
                NullPointerException.class,
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

        assertThrows(
                NullPointerException.class,
                () -> new CommandTestServices(
                        null,
                        services.workspaceTestService()));
        assertThrows(
                NullPointerException.class,
                () -> new CommandTestServices(
                        services.testRunService(),
                        null));
    }
}
