package com.zolt.cli;

import com.zolt.cli.command.AddCommand;
import com.zolt.cli.command.BuildCommand;
import com.zolt.cli.command.CheckCommand;
import com.zolt.cli.command.ClasspathCommand;
import com.zolt.cli.command.CleanCommand;
import com.zolt.cli.command.ConflictsCommand;
import com.zolt.cli.command.CoverageCommand;
import com.zolt.cli.command.DoctorCommand;
import com.zolt.cli.command.ExplainCommand;
import com.zolt.cli.command.IdeCommand;
import com.zolt.cli.command.InitCommand;
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
import com.zolt.perf.TimingFormat;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "zolt",
        mixinStandardHelpOptions = true,
        version = ZoltCli.VERSION,
        description = "The modern Java build toolkit.",
        subcommands = {
                CommandLine.HelpCommand.class,
                InitCommand.class,
                VersionCommand.class,
                UpdateCommand.class,
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

    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    static CommandLine newCommandLine() {
        return new CommandLine(new ZoltCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((exception, commandLine, parseResult) ->
                        commandLine.getCommandSpec().exitCodeOnExecutionException());
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
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

    public abstract static class StubCommand implements Runnable {
        private final String name;

        @Spec
        protected CommandSpec spec;

        StubCommand(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            spec.commandLine().getOut().printf("zolt %s is not implemented yet.%n", name);
            spec.commandLine().getOut().println("Next step: follow the matching followUp in followUps/.");
        }
    }

}
