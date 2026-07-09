package sh.zolt.cli.command;

import sh.zolt.cli.command.toolchain.CommandJavaToolchainJdkChecker;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toolchain.JavaToolchainExecutionService;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.store.ToolchainStore;
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
        WorkspaceToolchainServices services = workspaceToolchainServices();
        return (workspace, member) -> jdkChecker(
                member.directory(),
                workspace.root(),
                member.config(),
                commandName,
                services);
    }

    public WorkspaceTestRunServiceResolver workspaceTestRunServices(
            CommandServiceBundles.TestRunServiceFactory factory,
            String commandName) {
        WorkspaceToolchainServices services = workspaceToolchainServices();
        return (workspace, member) -> factory.create(jdkChecker(
                member.directory(),
                workspace.root(),
                member.config(),
                commandName,
                services));
    }

    public WorkspaceTestRunServiceResolver workspaceIntegrationTestRunServices(
            CommandServiceBundles.TestRunServiceFactory factory) {
        WorkspaceToolchainServices services = workspaceToolchainServices();
        return (workspace, member) -> factory.create(jdkChecker(
                member.directory(),
                workspace.root(),
                member.config().withBuildSettings(member.config().build().asIntegrationTestBuild()),
                "integration-test",
                services));
    }

    private CommandJavaToolchainJdkChecker jdkChecker(
            Path projectRoot,
            Path lockRoot,
            ProjectConfig config,
            String commandName,
            WorkspaceToolchainServices services) {
        return new CommandJavaToolchainJdkChecker(
                projectRoot,
                lockRoot,
                config,
                services.toolchains(),
                services.platform(),
                services.store(),
                commandName);
    }

    private WorkspaceToolchainServices workspaceToolchainServices() {
        return new WorkspaceToolchainServices(
                new JavaToolchainExecutionService(),
                HostPlatform.parse(toolchainTarget),
                new ToolchainStore(toolchainInstallRoot));
    }

    private record WorkspaceToolchainServices(
            JavaToolchainExecutionService toolchains,
            HostPlatform platform,
            ToolchainStore store) {
    }
}
