package sh.zolt.cli.command;

import sh.zolt.cli.command.toolchain.CommandJavaToolchainJdkChecker;
import sh.zolt.project.ProjectConfig;
import sh.zolt.workspace.service.WorkspaceJdkCheckerResolver;
import sh.zolt.workspace.service.WorkspaceTestRunServiceResolver;
import java.nio.file.Path;
import picocli.CommandLine.Option;

public final class CommandToolchainOptions {
    @Option(names = "--toolchain-target", hidden = true)
    private String toolchainTarget;

    @Option(names = "--toolchain-install-root", hidden = true)
    private Path toolchainInstallRoot;

    public CommandJavaToolchainJdkChecker jdkChecker(
            Path projectRoot,
            ProjectConfig config,
            String commandName) {
        return jdkChecker(projectRoot, projectRoot, config, commandName);
    }

    public CommandJavaToolchainJdkChecker jdkChecker(
            Path projectRoot,
            Path lockRoot,
            ProjectConfig config,
            String commandName) {
        return CommandJavaToolchainJdkChecker.forCommand(
                projectRoot,
                lockRoot,
                config,
                toolchainTarget,
                toolchainInstallRoot,
                commandName);
    }

    public WorkspaceJdkCheckerResolver workspaceJdkCheckers(String commandName) {
        return (workspace, member) -> jdkChecker(member.directory(), workspace.root(), member.config(), commandName);
    }

    public WorkspaceTestRunServiceResolver workspaceTestRunServices(
            CommandServiceBundles.TestRunServiceFactory factory,
            String commandName) {
        return (workspace, member) -> factory.create(jdkChecker(
                member.directory(),
                workspace.root(),
                member.config(),
                commandName));
    }

    public WorkspaceTestRunServiceResolver workspaceIntegrationTestRunServices(
            CommandServiceBundles.TestRunServiceFactory factory) {
        return (workspace, member) -> factory.create(jdkChecker(
                member.directory(),
                workspace.root(),
                member.config().withBuildSettings(member.config().build().asIntegrationTestBuild()),
                "integration-test"));
    }
}
