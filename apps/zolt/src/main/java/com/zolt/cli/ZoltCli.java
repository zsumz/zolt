package com.zolt.cli;

import com.zolt.cli.command.AddCommand;
import com.zolt.cli.command.BuildCommand;
import com.zolt.cli.command.CheckCommand;
import com.zolt.cli.command.ClasspathCommand;
import com.zolt.cli.command.CleanCommand;
import com.zolt.cli.command.ConflictsCommand;
import com.zolt.cli.command.ConfigCommand;
import com.zolt.cli.command.CoverageCommand;
import com.zolt.cli.command.DoctorCommand;
import com.zolt.cli.command.ExplainCommand;
import com.zolt.cli.command.IdeCommand;
import com.zolt.cli.command.InitCommand;
import com.zolt.cli.command.IntegrationTestCommand;
import com.zolt.cli.command.NativeCommand;
import com.zolt.cli.command.NativeSmokeCommand;
import com.zolt.cli.command.PackageCommand;
import com.zolt.cli.command.PlanCommand;
import com.zolt.cli.command.PlatformCommand;
import com.zolt.cli.command.PolicyCommand;
import com.zolt.cli.command.PublishCommand;
import com.zolt.cli.command.QuarkusCommand;
import com.zolt.cli.command.ReleaseArchiveCommand;
import com.zolt.cli.command.ReleaseVerifyCommand;
import com.zolt.cli.command.RemoveCommand;
import com.zolt.cli.command.ResolveCommand;
import com.zolt.cli.command.RunCommand;
import com.zolt.cli.command.RunPackageCommand;
import com.zolt.cli.command.SelfCheckCommand;
import com.zolt.cli.command.SelfParityCommand;
import com.zolt.cli.command.TestCommand;
import com.zolt.cli.command.TreeCommand;
import com.zolt.cli.command.UpdateCommand;
import com.zolt.cli.command.VersionCommand;
import com.zolt.cli.command.WhyCommand;
import com.zolt.cli.console.ColorMode;
import com.zolt.cli.console.ConsoleStyle;
import com.zolt.cli.console.ProgressMode;
import com.zolt.cli.console.ProgressPolicy;
import com.zolt.perf.TimingFormat;
import com.zolt.release.NativeUpdateNotice;
import com.zolt.release.NativeUpdateNoticeRequest;
import com.zolt.release.NativeUpdateNoticeService;
import com.zolt.release.ReleaseDistributionUrlLayout;
import com.zolt.release.ReleaseTarget;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

@Command(
        name = "zolt",
        version = ZoltCli.VERSION,
        description = "The modern Java build toolkit.",
        subcommands = {
                ZoltHelpCommand.class,
                InitCommand.class,
                VersionCommand.class,
                UpdateCommand.class,
                ConfigCommand.class,
                CheckCommand.class,
                AddCommand.class,
                RemoveCommand.class,
                PlatformCommand.class,
                ResolveCommand.class,
                TreeCommand.class,
                WhyCommand.class,
                PolicyCommand.class,
                ConflictsCommand.class,
                ExplainCommand.class,
                PlanCommand.class,
                ClasspathCommand.class,
                IdeCommand.class,
                QuarkusCommand.class,
                BuildCommand.class,
                RunCommand.class,
                TestCommand.class,
                IntegrationTestCommand.class,
                CoverageCommand.class,
                PackageCommand.class,
                PublishCommand.class,
                RunPackageCommand.class,
                NativeCommand.class,
                NativeSmokeCommand.class,
                ReleaseArchiveCommand.class,
                ReleaseVerifyCommand.class,
                SelfCheckCommand.class,
                SelfParityCommand.class,
                CleanCommand.class,
                DoctorCommand.class
        })
public final class ZoltCli implements Runnable {
    public static final String VERSION = "0.1.0-SNAPSHOT";

    @Option(
            names = "--color",
            scope = ScopeType.INHERIT,
            paramLabel = "<WHEN>",
            converter = ColorModeConverter.class,
            description = "Control color in human output: auto, always, or never.")
    private ColorMode colorMode = ColorMode.AUTO;

    @Option(
            names = "--progress",
            scope = ScopeType.INHERIT,
            paramLabel = "<WHEN>",
            converter = ProgressModeConverter.class,
            description = "Control progress in human output: auto, always, or never.")
    private ProgressMode progressMode = ProgressMode.AUTO;

    @Option(
            names = "--no-progress",
            scope = ScopeType.INHERIT,
            description = "Disable progress output.")
    private boolean noProgress;

    @Option(
            names = {"-q", "--quiet"},
            scope = ScopeType.INHERIT,
            description = "Suppress Zolt human summaries and auto progress.")
    private boolean quiet;

    @Option(names = "--update-check", scope = ScopeType.INHERIT, hidden = true)
    private String updateCheck = "auto";

    @Option(names = "--update-check-install-root", scope = ScopeType.INHERIT, hidden = true)
    private Path updateCheckInstallRoot = Path.of(System.getProperty("user.home"), ".zolt");

    @Option(names = "--update-check-channel-url", scope = ScopeType.INHERIT, hidden = true)
    private String updateCheckChannelUrl = new ReleaseDistributionUrlLayout().channelManifestUrl("stable");

    @Option(names = "--update-check-target", scope = ScopeType.INHERIT, hidden = true)
    private String updateCheckTarget;

    @Option(names = "--update-check-current-executable", scope = ScopeType.INHERIT, hidden = true)
    private Path updateCheckCurrentExecutable;

    @Option(names = "--update-check-state-dir", scope = ScopeType.INHERIT, hidden = true)
    private Path updateCheckStateDirectory;

    @Option(names = "--update-check-interval-seconds", scope = ScopeType.INHERIT, hidden = true)
    private long updateCheckIntervalSeconds = 86_400;

    @Option(names = "--list", description = "List available commands.")
    private boolean listCommands;

    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    static CommandLine newCommandLine() {
        ZoltCli rootCommand = new ZoltCli();
        CommandLine commandLine = new CommandLine(rootCommand)
                .setCaseInsensitiveEnumValuesAllowed(true);
        configureUniversalHelp(commandLine);
        CliUsageConfiguration.apply(commandLine, rootCommand::consoleStyle);
        configureExecutionHandling(commandLine, rootCommand);
        return commandLine;
    }

    private static void configureUniversalHelp(CommandLine commandLine) {
        commandLine.getCommandSpec().mixinStandardHelpOptions(true);
        commandLine.getSubcommands().values().forEach(ZoltCli::configureUniversalHelp);
    }

    private static void configureExecutionHandling(CommandLine commandLine, ZoltCli rootCommand) {
        commandLine.setExecutionStrategy(parseResult -> rootCommand.executeWithUpdateNotice(commandLine, parseResult));
        commandLine.setExecutionExceptionHandler(ZoltCli::handleExecutionException);
        commandLine.getSubcommands().values().forEach(ZoltCli::configureExecutionHandling);
    }

    private static void configureExecutionHandling(CommandLine commandLine) {
        commandLine.setExecutionExceptionHandler(ZoltCli::handleExecutionException);
        commandLine.getSubcommands().values().forEach(ZoltCli::configureExecutionHandling);
    }

    private static int handleExecutionException(
            Exception exception,
            CommandLine parsedCommandLine,
            CommandLine.ParseResult parseResult) {
        if (!PrintedUserException.alreadyPrinted(exception)) {
            CommandHumanOutput output = CommandHumanOutput.errors(parsedCommandLine.getCommandSpec());
            output.error(exception.getMessage());
            parsedCommandLine.getErr().flush();
        }
        return parsedCommandLine.getCommandSpec().exitCodeOnExecutionException();
    }

    private int executeWithUpdateNotice(CommandLine commandLine, ParseResult parseResult) {
        int exitCode = new CommandLine.RunLast().execute(parseResult);
        if (exitCode == 0) {
            printUpdateNotice(commandLine, parseResult);
        }
        return exitCode;
    }

    private void printUpdateNotice(CommandLine commandLine, ParseResult parseResult) {
        if (parseResult.isUsageHelpRequested()
                || parseResult.isVersionHelpRequested()
                || shouldSkipUpdateNotice(parseResult)) {
            return;
        }
        boolean force = updateCheckMode().equals("always");
        if (!force && System.console() == null) {
            return;
        }
        Path currentExecutable = effectiveUpdateCheckExecutable();
        if (currentExecutable == null) {
            return;
        }
        try {
            ReleaseTarget target = updateCheckTarget == null ? ReleaseTarget.current() : ReleaseTarget.fromId(updateCheckTarget);
            NativeUpdateNoticeService service = new NativeUpdateNoticeService();
            service.check(new NativeUpdateNoticeRequest(
                            updateCheckInstallRoot,
                            currentExecutable,
                            URI.create(updateCheckChannelUrl),
                            target,
                            updateCheckStateDirectory == null ? updateCheckInstallRoot.resolve("state") : updateCheckStateDirectory,
                            Instant.now(),
                            Duration.ofSeconds(Math.max(0, updateCheckIntervalSeconds)),
                            updateCheckDisabled(),
                            updateCheckOffline(),
                            updateCheckCi(),
                            force || System.console() != null))
                    .map(NativeUpdateNotice::message)
                    .ifPresent(message -> {
                        commandLine.getErr().println(message);
                        commandLine.getErr().flush();
                    });
        } catch (RuntimeException exception) {
            // Passive update notices must never fail the user's original command.
        }
    }

    private boolean shouldSkipUpdateNotice(ParseResult parseResult) {
        if (quiet || updateCheckMode().equals("never")) {
            return true;
        }
        String commandName = leafCommandName(parseResult);
        return commandName.equals("zolt")
                || commandName.equals("help")
                || commandName.equals("update");
    }

    private static String leafCommandName(ParseResult parseResult) {
        ParseResult current = parseResult;
        while (current.hasSubcommand()) {
            current = current.subcommand();
        }
        return current.commandSpec().name();
    }

    private Path effectiveUpdateCheckExecutable() {
        if (updateCheckCurrentExecutable != null) {
            return updateCheckCurrentExecutable;
        }
        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .orElse(null);
    }

    private String updateCheckMode() {
        String normalized = updateCheck.toLowerCase(Locale.ROOT).strip();
        if (normalized.equals("always") || normalized.equals("never") || normalized.equals("auto")) {
            return normalized;
        }
        return "auto";
    }

    private boolean updateCheckDisabled() {
        String env = System.getenv().getOrDefault("ZOLT_UPDATE_CHECK", "").toLowerCase(Locale.ROOT).strip();
        return updateCheckMode().equals("never")
                || env.equals("0")
                || env.equals("off")
                || env.equals("false")
                || env.equals("never")
                || env.equals("disabled");
    }

    private boolean updateCheckOffline() {
        String env = System.getenv().getOrDefault("ZOLT_OFFLINE", "").toLowerCase(Locale.ROOT).strip();
        return env.equals("1") || env.equals("true") || env.equals("yes");
    }

    private boolean updateCheckCi() {
        return System.getenv().containsKey("CI") || System.getenv().containsKey("GITHUB_ACTIONS");
    }

    @Override
    public void run() {
        if (listCommands) {
            spec.commandLine().getOut().println(consoleStyle().helpHeading("Commands:"));
            spec.commandLine().getOut().print(RootCommandListRenderer.render(spec.commandLine(), consoleStyle()));
            spec.commandLine().getOut().flush();
            return;
        }
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    ConsoleStyle consoleStyle() {
        boolean interactive = System.console() != null;
        return ConsoleStyle.of(colorMode, interactive, System.getenv());
    }

    ProgressPolicy progressPolicy() {
        boolean interactiveStderr = System.console() != null;
        boolean suppressProgress = noProgress || (quiet && progressMode != ProgressMode.ALWAYS);
        return ProgressPolicy.of(progressMode, suppressProgress, interactiveStderr, System.getenv());
    }

    boolean quiet() {
        return quiet;
    }

    public static final class TimingOptions {
        @Option(names = "--timings", description = "Print command timing information.")
        private boolean enabled;

        @Option(names = "--timings-format", description = "Timing output format: text or json.")
        private TimingFormat format = TimingFormat.TEXT;

        public boolean enabled() {
            return enabled;
        }

        public TimingFormat format() {
            return format;
        }
    }

}
