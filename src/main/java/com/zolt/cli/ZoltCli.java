package com.zolt.cli;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildService;
import com.zolt.build.CleanException;
import com.zolt.build.CleanResult;
import com.zolt.build.CleanService;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.PackageResult;
import com.zolt.build.PackageService;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.RunException;
import com.zolt.build.RunResult;
import com.zolt.build.RunService;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.TestRunException;
import com.zolt.build.TestRunResult;
import com.zolt.build.TestRunService;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathFormatter;
import com.zolt.classpath.ClasspathSet;
import com.zolt.conflict.DependencyConflictFormatter;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.tree.DependencyTreeFormatter;
import com.zolt.tree.DependencyWhyException;
import com.zolt.tree.DependencyWhyFormatter;
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
                ZoltCli.ClasspathCommand.class,
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
    public static final class RemoveCommand implements Runnable {
        @Parameters(
                arity = "1..2",
                paramLabel = "[test] GROUP:ARTIFACT",
                description = "Dependency coordinate, optionally prefixed with `test` for test dependencies.")
        private List<String> arguments;

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
                RemoveRequest request = parseRequest(arguments);
                Path configPath = workingDirectory.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                Map<String, String> dependencies = dependencies(config, request.section());
                String section = sectionName(request.section());
                if (!dependencies.containsKey(request.coordinate())) {
                    spec.commandLine().getOut().println("Dependency " + request.coordinate()
                            + " is not present in [" + section + "]; nothing to remove.");
                    return;
                }
                ProjectConfig updated = tomlWriter.removeDependency(config, request.section(), request.coordinate());
                tomlWriter.write(configPath, updated);
                spec.commandLine().getOut().println(
                        "Removed dependency " + request.coordinate() + " from [" + section + "]");
                printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
            } catch (RemoveCommandException | CoordinateParseException | ResolveException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private RemoveRequest parseRequest(List<String> values) {
            if (values.size() == 2 && !"test".equals(values.get(0))) {
                throw new RemoveCommandException(
                        "Unexpected dependency section `" + values.get(0) + "`. Use `zolt remove test group:artifact` for test dependencies.");
            }
            DependencySection section = values.size() == 2 ? DependencySection.TEST : DependencySection.MAIN;
            String rawCoordinate = values.size() == 2 ? values.get(1) : values.get(0);
            Coordinate coordinate = coordinateParser.parse(rawCoordinate);
            return new RemoveRequest(section, coordinate.groupId() + ":" + coordinate.artifactId());
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
    public static final class TreeCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                String output = new DependencyTreeFormatter().format(
                        config,
                        new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock")));
                printAndFlush(spec, output);
            } catch (LockfileReadException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "why", description = "Explain why a package is present.")
    public static final class WhyCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT", description = "Package id to explain.")
        private String packageId;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        private final CoordinateParser coordinateParser = new CoordinateParser();

        @Override
        public void run() {
            try {
                Coordinate coordinate = coordinateParser.parse(packageId);
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                String output = new DependencyWhyFormatter().format(
                        config,
                        new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock")),
                        new PackageId(coordinate.groupId(), coordinate.artifactId()));
                printAndFlush(spec, output);
            } catch (CoordinateParseException | DependencyWhyException | LockfileReadException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "conflicts", description = "Show version conflicts and selected versions.")
    public static final class ConflictsCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                String output = new DependencyConflictFormatter().format(
                        new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock")));
                printAndFlush(spec, output);
            } catch (LockfileReadException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "classpath", description = "Print a classpath from zolt.lock.")
    public static final class ClasspathCommand implements Runnable {
        enum Kind {
            COMPILE,
            RUNTIME,
            TEST
        }

        @Parameters(index = "0", paramLabel = "compile|runtime|test", description = "Classpath kind to print.")
        private Kind kind;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
                ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(
                        lockfileReader.read(workingDirectory.resolve("zolt.lock")),
                        cacheRoot));
                String output = new ClasspathFormatter().format(switch (kind) {
                    case COMPILE -> classpaths.compile();
                    case RUNTIME -> classpaths.runtime();
                    case TEST -> classpaths.test();
                });
                printAndFlush(spec, output);
            } catch (LockfileReadException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "build", description = "Compile main Java sources with the resolved compile classpath.")
    public static final class BuildCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                BuildResult result = new BuildService().build(workingDirectory, config, cacheRoot);
                if (result.resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println("Compiled " + result.sourceCount() + " main source files");
                spec.commandLine().getOut().println("Wrote classes to " + result.outputDirectory());
            } catch (BuildException
                    | JavacException
                    | ResourceCopyException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "run", description = "Build and run the configured main class.")
    public static final class RunCommand implements Runnable {
        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed to the application after `--`.")
        private List<String> arguments = List.of();

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                RunResult result = new RunService().run(workingDirectory, config, cacheRoot, arguments);
                String output = result.javaRunResult().output();
                printAndFlush(spec, output);
                if (!output.isEmpty() && !output.endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().println("Ran " + result.javaRunResult().mainClass());
            } catch (BuildException
                    | JavacException
                    | JavaRunException
                    | ResourceCopyException
                    | RunException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "test", description = "Compile and run tests, starting with JUnit support.")
    public static final class TestCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                TestRunResult result = new TestRunService().runTests(workingDirectory, config, cacheRoot);
                printAndFlush(spec, result.output());
                if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().println("Tests passed");
            } catch (BuildException
                    | JavacException
                    | JavaRunException
                    | ResourceCopyException
                    | TestRunException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "package", description = "Package compiled classes into a jar.")
    public static final class PackageCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                PackageResult result = new PackageService().packageJar(workingDirectory, config, cacheRoot);
                if (result.buildResult().resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println("Packaged " + result.entryCount() + " compiled files");
                if (result.hasMainClass()) {
                    spec.commandLine().getOut().println("Included Main-Class manifest entry");
                    spec.commandLine().getOut().println("Run with: java -jar " + result.jarPath());
                } else {
                    spec.commandLine().getOut().println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
                }
                spec.commandLine().getOut().println("Thin jar: dependencies are not bundled.");
                spec.commandLine().getOut().println("Future: zolt run-package will run thin jars with dependency classpaths.");
                spec.commandLine().getOut().println("Wrote jar to " + result.jarPath());
            } catch (BuildException
                    | JavacException
                    | ManifestGenerationException
                    | PackageException
                    | ResourceCopyException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "clean", description = "Remove project build output.")
    public static final class CleanCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                CleanResult result = new CleanService().clean(workingDirectory, config.build());
                if (result.deletedPaths().isEmpty()) {
                    spec.commandLine().getOut().println("Nothing to clean");
                    return;
                }
                spec.commandLine().getOut().println("Deleted " + result.deletedCount() + " build output paths");
                for (Path path : result.deletedPaths()) {
                    spec.commandLine().getOut().println("Deleted " + path);
                }
            } catch (CleanException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "doctor", description = "Inspect local Java/JDK/Zolt project health.")
    public static final class DoctorCommand implements Runnable {
        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                JdkStatus status = new JdkDetector().detect(config.project().java());
                printJdkStatus(spec, status);
                if (!status.ok()) {
                    throw new CommandLine.ExecutionException(spec.commandLine(), "JDK check failed.");
                }
            } catch (ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
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

    private static void printAndFlush(CommandSpec spec, String output) {
        spec.commandLine().getOut().print(output);
        spec.commandLine().getOut().flush();
    }

    private static void printJdkStatus(CommandSpec spec, JdkStatus status) {
        spec.commandLine().getOut().println("JDK status: " + (status.ok() ? "ok" : "error"));
        spec.commandLine().getOut().println("JAVA_HOME: " + status.javaHome().map(Path::toString).orElse("not set"));
        spec.commandLine().getOut().println("java: " + status.java().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("javac: " + status.javac().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("jar: " + status.jar().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("version: " + status.version().orElse("unknown"));
        for (String problem : status.problems()) {
            spec.commandLine().getErr().println("error: " + problem);
        }
    }

    private record AddRequest(DependencySection section, String coordinate, String version) {
    }

    private record RemoveRequest(DependencySection section, String coordinate) {
    }

    private static Map<String, String> dependencies(ProjectConfig config, DependencySection section) {
        return section == DependencySection.TEST ? config.testDependencies() : config.dependencies();
    }

    private static String sectionName(DependencySection section) {
        return section == DependencySection.TEST ? "test.dependencies" : "dependencies";
    }

    private static final class AddCommandException extends RuntimeException {
        private AddCommandException(String message) {
            super(message);
        }
    }

    private static final class RemoveCommandException extends RuntimeException {
        private RemoveCommandException(String message) {
            super(message);
        }
    }
}
