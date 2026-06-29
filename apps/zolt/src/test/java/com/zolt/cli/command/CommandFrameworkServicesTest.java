package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import com.zolt.cli.command.CommandServiceClusters.CommandBuildFrameworkServices;
import com.zolt.cli.command.CommandServiceClusters.CommandConfigEditServices;
import com.zolt.cli.command.CommandServiceClusters.CommandPackageFrameworkServices;
import com.zolt.cli.command.CommandServiceClusters.CommandRunFrameworkServices;
import com.zolt.cli.command.CommandServiceClusters.CommandTestFrameworkServices;
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
    void nativeCommandServicesOwnsDefaultNativeWiring() {
        CommandNativeServices services = CommandFrameworkServices.nativeCommandServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.nativeBuildService());
        assertNotNull(services.workspaceNativeBuildService());
    }

    @Test
    void versionAliasCommandServicesOwnsDefaultVersionAliasWiring() {
        CommandVersionAliasServices services = CommandFrameworkServices.versionAliasCommandServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.tomlWriter());
        assertNotNull(services.resolveService());
    }

    @Test
    void configEditServicesOwnsDefaultConfigEditWiring() {
        CommandConfigEditServices services = CommandFrameworkServices.configEditServices();

        assertNotNull(services.tomlParser());
        assertNotNull(services.tomlWriter());
        assertNotNull(services.resolveService());
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
    void resolveCommandServicesOwnsDefaultResolveWiring() {
        CommandResolveServices services = CommandFrameworkServices.resolveCommandServices();

        assertNotNull(services.resolveService());
        assertNotNull(services.workspaceResolveService());
    }

    @Test
    void buildCommandServicesOwnsDefaultBuildWiring() {
        CommandBuildServices services = CommandFrameworkServices.buildCommandServices();

        assertNotNull(services.buildService());
        assertNotNull(services.workspaceBuildService());
        assertNotNull(services.frameworkBuildAugmenter());
    }

    @Test
    void buildFrameworkServicesOwnsDefaultBuildFrameworkWiring() {
        CommandBuildFrameworkServices services = CommandFrameworkServices.buildFrameworkServices();

        assertNotNull(services.frameworkBuildAugmenter());
        assertNotNull(services.resolveService());
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
    void packageFrameworkServicesOwnsDefaultPackageFrameworkWiring() {
        CommandPackageFrameworkServices services = CommandFrameworkServices.packageFrameworkServices();

        assertNotNull(services.packageAugmenter());
        assertNotNull(services.packagePlanService());
    }

    @Test
    void runPackageCommandServicesOwnsDefaultRunPackageWiring() {
        CommandRunPackageServices services = CommandFrameworkServices.runPackageCommandServices();

        assertNotNull(services.runPackageService());
        assertNotNull(services.workspaceRunPackageService());
    }

    @Test
    void runCommandServicesOwnsDefaultRunWiring() {
        CommandRunServices services = CommandFrameworkServices.runCommandServices();

        assertNotNull(services.runService());
        assertNotNull(services.workspaceRunService());
    }

    @Test
    void runFrameworkServicesOwnsDefaultRunFrameworkWiring() {
        CommandRunFrameworkServices services = CommandFrameworkServices.runFrameworkServices();

        assertNotNull(services.frameworkRunAugmenter());
        assertNotNull(services.resolveService());
    }

    @Test
    void testCommandServicesOwnsDefaultTestWiring() {
        CommandTestServices services = CommandFrameworkServices.testCommandServices();

        assertNotNull(services.testRunService());
        assertNotNull(services.workspaceTestService());
    }

    @Test
    void testFrameworkServicesOwnsDefaultTestFrameworkWiring() {
        CommandTestFrameworkServices services = CommandFrameworkServices.testFrameworkServices();

        assertNotNull(services.frameworkTestRunner());
        assertNotNull(services.resolveService());
    }
}
