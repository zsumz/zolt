package com.zolt.cli;

import com.zolt.cli.command.build.BuildCommand;
import com.zolt.cli.command.build.CleanCommand;
import com.zolt.cli.command.build.PlanCommand;
import com.zolt.cli.command.build.RunCommand;
import com.zolt.cli.command.config.ConfigCommand;
import com.zolt.cli.command.config.InitCommand;
import com.zolt.cli.command.dependency.AddCommand;
import com.zolt.cli.command.dependency.ConflictsCommand;
import com.zolt.cli.command.dependency.PlatformCommand;
import com.zolt.cli.command.dependency.RemoveCommand;
import com.zolt.cli.command.dependency.VersionCommand;
import com.zolt.cli.command.ide.IdeCommand;
import com.zolt.cli.command.insight.ExplainCommand;
import com.zolt.cli.command.insight.TreeCommand;
import com.zolt.cli.command.insight.WhyCommand;
import com.zolt.cli.command.nativeimage.NativeCommand;
import com.zolt.cli.command.nativeimage.NativeSmokeCommand;
import com.zolt.cli.command.packaging.PackageCommand;
import com.zolt.cli.command.packaging.RunPackageCommand;
import com.zolt.cli.command.packaging.SelfParityCommand;
import com.zolt.cli.command.publish.PublishCommand;
import com.zolt.cli.command.publish.ReleaseArchiveCommand;
import com.zolt.cli.command.publish.ReleaseVerifyCommand;
import com.zolt.cli.command.quality.CheckCommand;
import com.zolt.cli.command.quality.CoverageCommand;
import com.zolt.cli.command.quality.DoctorCommand;
import com.zolt.cli.command.quality.PolicyCommand;
import com.zolt.cli.command.quarkus.QuarkusCommand;
import com.zolt.cli.command.resolve.ClasspathCommand;
import com.zolt.cli.command.resolve.ResolveCommand;
import com.zolt.cli.command.selfhost.SelfCheckCommand;
import com.zolt.cli.command.testcmd.IntegrationTestCommand;
import com.zolt.cli.command.testcmd.TestCommand;
import com.zolt.cli.command.update.UpdateCommand;
import com.zolt.cli.console.ColorMode;
import com.zolt.cli.console.ConsoleStyle;
import com.zolt.cli.console.ProgressMode;
import com.zolt.cli.console.ProgressPolicy;
import com.zolt.perf.TimingFormat;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
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

    @Mixin
    private ZoltUpdateNoticeHook updateNoticeHook = new ZoltUpdateNoticeHook();

    @Option(names = "--list", description = "List available commands.")
    private boolean listCommands;

    @Spec
    private CommandLine.Model.CommandSpec spec;

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
        commandLine.setExecutionStrategy(parseResult -> rootCommand.updateNoticeHook.executeWithNotice(
                commandLine,
                parseResult,
                rootCommand.quiet));
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
