package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CommandFrameworkServicesTest {
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
}
