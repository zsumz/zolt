package com.zolt.cli;

import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
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
    public static final class InitCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "NAME", description = "Project directory to create.")
        private String name;

        @Option(names = "--group", description = "Java package group for generated sources.")
        private String group = "com.example";

        @Option(names = "--java", description = "Java version for zolt.toml.")
        private String javaVersion = "21";

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectInitResult result = new ProjectInitializer().init(workingDirectory, name, group, javaVersion);
                spec.commandLine().getOut().println("Created Zolt project at " + result.projectDirectory());
                spec.commandLine().getOut().println("Next: cd " + result.projectDirectory().getFileName());
            } catch (ProjectInitException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "add", description = "Add a dependency to zolt.toml and refresh zolt.lock.")
    public static final class AddCommand implements Runnable {
        @Parameters(
                arity = "1..2",
                paramLabel = "[test] GROUP:ARTIFACT:VERSION",
                description = "Dependency coordinate, optionally prefixed with `test` for test dependencies.")
        private List<String> arguments;

        @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
        private boolean noResolve;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        private final CoordinateParser coordinateParser = new CoordinateParser();
        private final ZoltTomlParser tomlParser = new ZoltTomlParser();
        private final ZoltTomlWriter tomlWriter = new ZoltTomlWriter();
        private final ResolveService resolveService = new ResolveService();

        @Override
        public void run() {
            try {
                AddRequest request = parseRequest(arguments);
                Path configPath = workingDirectory.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                ProjectConfig updated = updateConfig(config, request);
                tomlWriter.write(configPath, updated);
                printAddSummary(config, request);
                if (noResolve) {
                    spec.commandLine().getOut().println("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                    return;
                }
                printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
            } catch (AddCommandException | CoordinateParseException | ResolveException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private AddRequest parseRequest(List<String> values) {
            if (values.size() == 2 && !"test".equals(values.get(0))) {
                throw new AddCommandException(
                        "Unexpected dependency section `" + values.get(0) + "`. Use `zolt add test group:artifact:version` for test dependencies.");
            }
            DependencySection section = values.size() == 2 ? DependencySection.TEST : DependencySection.MAIN;
            String rawCoordinate = values.size() == 2 ? values.get(1) : values.get(0);
            Coordinate coordinate = coordinateParser.parse(rawCoordinate);
            String version = coordinate.version().orElseThrow(() -> new AddCommandException(
                    "Dependency coordinate must include a version. Use `group:artifact:version`."));
            return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), version);
        }

        private ProjectConfig updateConfig(ProjectConfig config, AddRequest request) {
            return tomlWriter.addDependency(config, request.section(), request.coordinate(), request.version());
        }

        private void printAddSummary(ProjectConfig original, AddRequest request) {
            Map<String, String> dependencies = request.section() == DependencySection.TEST
                    ? original.testDependencies()
                    : original.dependencies();
            String section = request.section() == DependencySection.TEST ? "test.dependencies" : "dependencies";
            String existing = dependencies.get(request.coordinate());
            if (request.version().equals(existing)) {
                spec.commandLine().getOut().println("Dependency " + request.coordinate() + ":" + request.version()
                        + " already exists in [" + section + "]");
            } else if (existing != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + existing + " to " + request.version() + " in [" + section + "]");
            } else {
                spec.commandLine().getOut().println("Added dependency " + request.coordinate() + ":" + request.version()
                        + " to [" + section + "]");
            }
        }
    }

    @Command(name = "remove", description = "Remove a dependency and prune unused transitive packages.")
    public static final class RemoveCommand extends StubCommand {
        public RemoveCommand() {
            super("remove");
        }
    }

    @Command(name = "resolve", description = "Resolve dependencies, download artifacts, and write zolt.lock.")
    public static final class ResolveCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ResolveResult result = new ResolveService().resolve(
                        workingDirectory,
                        new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")),
                        cacheRoot);
                printResolveResult(spec, result);
            } catch (ResolveException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
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

    private static void printResolveResult(CommandSpec spec, ResolveResult result) {
        spec.commandLine().getOut().println("Resolved " + result.resolvedCount() + " packages");
        spec.commandLine().getOut().println("Downloaded " + result.downloadCount() + " artifacts");
        spec.commandLine().getOut().println("Conflicts " + result.conflictCount());
        spec.commandLine().getOut().println("Wrote " + result.lockfilePath());
    }

    private record AddRequest(DependencySection section, String coordinate, String version) {
    }

    private static final class AddCommandException extends RuntimeException {
        private AddCommandException(String message) {
            super(message);
        }
    }
}
