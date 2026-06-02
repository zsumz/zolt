package com.zolt.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "zolt",
        mixinStandardHelpOptions = true,
        version = "zolt 0.1.0-SNAPSHOT",
        description = "A Cargo-inspired Java project tool.",
        subcommands = {
                CommandLine.HelpCommand.class,
                ZoltCli.InitCommand.class,
                ZoltCli.AddCommand.class,
                ZoltCli.RemoveCommand.class,
                ZoltCli.ResolveCommand.class,
                ZoltCli.TreeCommand.class,
                ZoltCli.WhyCommand.class,
                ZoltCli.ConflictsCommand.class,
                ZoltCli.BuildCommand.class,
                ZoltCli.RunCommand.class,
                ZoltCli.TestCommand.class,
                ZoltCli.PackageCommand.class,
                ZoltCli.CleanCommand.class,
                ZoltCli.DoctorCommand.class
        })
public final class ZoltCli implements Runnable {
    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    static CommandLine newCommandLine() {
        return new CommandLine(new ZoltCli()).setCaseInsensitiveEnumValuesAllowed(true);
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "init", description = "Create a new Zolt project.")
    public static final class InitCommand extends StubCommand {
        public InitCommand() {
            super("init");
        }
    }

    @Command(name = "add", description = "Add a dependency to zolt.toml and refresh zolt.lock.")
    public static final class AddCommand extends StubCommand {
        public AddCommand() {
            super("add");
        }
    }

    @Command(name = "remove", description = "Remove a dependency and prune unused transitive packages.")
    public static final class RemoveCommand extends StubCommand {
        public RemoveCommand() {
            super("remove");
        }
    }

    @Command(name = "resolve", description = "Resolve dependencies, download artifacts, and write zolt.lock.")
    public static final class ResolveCommand extends StubCommand {
        public ResolveCommand() {
            super("resolve");
        }
    }

    @Command(name = "tree", description = "Display the resolved dependency graph.")
    public static final class TreeCommand extends StubCommand {
        public TreeCommand() {
            super("tree");
        }
    }

    @Command(name = "why", description = "Explain why a package is present.")
    public static final class WhyCommand extends StubCommand {
        public WhyCommand() {
            super("why");
        }
    }

    @Command(name = "conflicts", description = "Show version conflicts and selected versions.")
    public static final class ConflictsCommand extends StubCommand {
        public ConflictsCommand() {
            super("conflicts");
        }
    }

    @Command(name = "build", description = "Compile main Java sources with the resolved compile classpath.")
    public static final class BuildCommand extends StubCommand {
        public BuildCommand() {
            super("build");
        }
    }

    @Command(name = "run", description = "Build and run the configured main class.")
    public static final class RunCommand extends StubCommand {
        public RunCommand() {
            super("run");
        }
    }

    @Command(name = "test", description = "Compile and run tests, starting with JUnit support.")
    public static final class TestCommand extends StubCommand {
        public TestCommand() {
            super("test");
        }
    }

    @Command(name = "package", description = "Package compiled classes into a jar.")
    public static final class PackageCommand extends StubCommand {
        public PackageCommand() {
            super("package");
        }
    }

    @Command(name = "clean", description = "Remove project build output.")
    public static final class CleanCommand extends StubCommand {
        public CleanCommand() {
            super("clean");
        }
    }

    @Command(name = "doctor", description = "Inspect local Java/JDK/Zolt project health.")
    public static final class DoctorCommand extends StubCommand {
        public DoctorCommand() {
            super("doctor");
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
