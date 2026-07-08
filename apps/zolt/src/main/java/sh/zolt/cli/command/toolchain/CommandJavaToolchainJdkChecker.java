package sh.zolt.cli.command.toolchain;

import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toolchain.JavaToolchainEnvironment;
import sh.zolt.toolchain.JavaToolchainExecutionService;
import sh.zolt.toolchain.jvm.ResolvedJavaToolchain;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.store.ToolchainStore;
import java.nio.file.Path;
import java.util.Optional;

public final class CommandJavaToolchainJdkChecker implements JdkChecker {
    private final Path projectRoot;
    private final Path lockRoot;
    private final ProjectConfig config;
    private final JavaToolchainExecutionService toolchains;
    private final HostPlatform platform;
    private final ToolchainStore store;
    private final String commandName;

    public static CommandJavaToolchainJdkChecker forCommand(
            Path projectRoot,
            ProjectConfig config,
            String toolchainTarget,
            Path toolchainInstallRoot,
            String commandName) {
        return forCommand(projectRoot, projectRoot, config, toolchainTarget, toolchainInstallRoot, commandName);
    }

    public static CommandJavaToolchainJdkChecker forCommand(
            Path projectRoot,
            Path lockRoot,
            ProjectConfig config,
            String toolchainTarget,
            Path toolchainInstallRoot,
            String commandName) {
        return new CommandJavaToolchainJdkChecker(
                projectRoot,
                lockRoot,
                config,
                new JavaToolchainExecutionService(),
                HostPlatform.parse(toolchainTarget),
                new ToolchainStore(toolchainInstallRoot),
                commandName);
    }

    public CommandJavaToolchainJdkChecker(
            Path projectRoot,
            ProjectConfig config,
            JavaToolchainExecutionService toolchains,
            HostPlatform platform,
            ToolchainStore store,
            String commandName) {
        this(projectRoot, projectRoot, config, toolchains, platform, store, commandName);
    }

    public CommandJavaToolchainJdkChecker(
            Path projectRoot,
            Path lockRoot,
            ProjectConfig config,
            JavaToolchainExecutionService toolchains,
            HostPlatform platform,
            ToolchainStore store,
            String commandName) {
        this.projectRoot = projectRoot;
        this.lockRoot = lockRoot;
        this.config = config;
        this.toolchains = toolchains;
        this.platform = platform;
        this.store = store;
        this.commandName = commandName;
    }

    @Override
    public JdkStatus detect(String requiredVersion) {
        JavaToolchainEnvironment environment = toolchains.environment(
                projectRoot,
                lockRoot,
                config,
                platform,
                store,
                "Java toolchain is not ready for " + commandName,
                "Run `zolt toolchain status` for details, then `zolt toolchain sync`, or choose a project with a usable Java toolchain.");
        ResolvedJavaToolchain resolved = environment.resolved();
        return new JdkStatus(
                Optional.of(environment.javaHome()),
                resolved.java().map(CommandJavaToolchainJdkChecker::absolute),
                resolved.javac().map(CommandJavaToolchainJdkChecker::absolute),
                resolved.jar().map(CommandJavaToolchainJdkChecker::absolute),
                runtimeVersion(resolved),
                requiredVersion);
    }

    private static Optional<String> runtimeVersion(ResolvedJavaToolchain resolved) {
        Optional<String> featureVersion = resolved.runtime().featureVersion();
        return featureVersion.isPresent() ? featureVersion : resolved.runtime().version();
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
