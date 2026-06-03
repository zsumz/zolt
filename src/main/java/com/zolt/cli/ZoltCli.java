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
import com.zolt.build.NativeBuildResult;
import com.zolt.build.NativeBuildService;
import com.zolt.build.NativeImageException;
import com.zolt.build.PackageException;
import com.zolt.build.PackageResult;
import com.zolt.build.PackageService;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.RunException;
import com.zolt.build.RunPackageException;
import com.zolt.build.RunPackageResult;
import com.zolt.build.RunPackageService;
import com.zolt.build.RunResult;
import com.zolt.build.RunService;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.TestRunException;
import com.zolt.build.TestRunResult;
import com.zolt.build.TestRunService;
import com.zolt.cache.ArtifactCacheException;
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
import com.zolt.release.ReleaseArchiveException;
import com.zolt.release.ReleaseArchiveResult;
import com.zolt.release.ReleaseArchiveService;
import com.zolt.release.ReleaseTarget;
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
        description = "The modern Java build toolkit.",
        subcommands = {
                CommandLine.HelpCommand.class,
                ZoltCli.InitCommand.class,
                ZoltCli.AddCommand.class,
                ZoltCli.RemoveCommand.class,
                ZoltCli.PlatformCommand.class,
                ZoltCli.ResolveCommand.class,
                ZoltCli.TreeCommand.class,
                ZoltCli.WhyCommand.class,
                ZoltCli.ConflictsCommand.class,
                ZoltCli.ClasspathCommand.class,
                ZoltCli.BuildCommand.class,
                ZoltCli.RunCommand.class,
                ZoltCli.TestCommand.class,
                ZoltCli.PackageCommand.class,
                ZoltCli.RunPackageCommand.class,
                ZoltCli.NativeCommand.class,
                ZoltCli.ReleaseArchiveCommand.class,
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
                paramLabel = "[test] GROUP:ARTIFACT[:VERSION]",
                description = "Dependency coordinate, optionally prefixed with `test` for test dependencies.")
        private List<String> arguments;

        @Option(names = "--managed", description = "Use a version managed by a declared platform.")
        private boolean managed;

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
            } catch (AddCommandException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
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
            if (managed && coordinate.version().isPresent()) {
                throw new AddCommandException(
                        "Managed dependency coordinate must not include a version. Use `group:artifact`.");
            }
            if (managed) {
                return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), "", true);
            }
            String version = coordinate.version().orElseThrow(() -> new AddCommandException(
                    "Dependency coordinate must include a version. Use `group:artifact:version` or add `--managed` when a declared platform should provide the version."));
            return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), version, false);
        }

        private ProjectConfig updateConfig(ProjectConfig config, AddRequest request) {
            if (request.managed()) {
                return tomlWriter.addManagedDependency(config, request.section(), request.coordinate());
            }
            return tomlWriter.addDependency(config, request.section(), request.coordinate(), request.version());
        }

        private void printAddSummary(ProjectConfig original, AddRequest request) {
            Map<String, String> dependencies = request.section() == DependencySection.TEST
                    ? original.testDependencies()
                    : original.dependencies();
            String section = request.section() == DependencySection.TEST ? "test.dependencies" : "dependencies";
            String existing = dependencies.get(request.coordinate());
            boolean existingManaged = managedDependencies(original, request.section()).contains(request.coordinate());
            if (request.managed()) {
                if (existingManaged) {
                    spec.commandLine().getOut().println("Dependency " + request.coordinate()
                            + " already uses a platform-managed version in [" + section + "]");
                } else if (existing != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from " + existing + " to platform-managed version in [" + section + "]");
                } else {
                    spec.commandLine().getOut().println("Added dependency " + request.coordinate()
                            + " with a platform-managed version to [" + section + "]");
                }
                return;
            }
            if (request.version().equals(existing)) {
                spec.commandLine().getOut().println("Dependency " + request.coordinate() + ":" + request.version()
                        + " already exists in [" + section + "]");
            } else if (existingManaged) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from managed version to " + request.version() + " in [" + section + "]");
            } else if (existing != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + existing + " to " + request.version() + " in [" + section + "]");
            } else {
                spec.commandLine().getOut().println("Added dependency " + request.coordinate() + ":" + request.version()
                        + " to [" + section + "]");
            }
        }
    }

    @Command(
            name = "platform",
            mixinStandardHelpOptions = true,
            description = "Manage BOM/platform imports in zolt.toml.",
            subcommands = {
                    PlatformCommand.AddCommand.class,
                    PlatformCommand.RemoveCommand.class
            })
    public static final class PlatformCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }

        @Command(
                name = "add",
                mixinStandardHelpOptions = true,
                description = "Add a platform BOM import to zolt.toml and refresh zolt.lock.")
        public static final class AddCommand implements Runnable {
            @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT:VERSION", description = "Platform BOM coordinate.")
            private String coordinate;

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
                    Coordinate parsed = coordinateParser.parse(coordinate);
                    String version = parsed.version().orElseThrow(() -> new PlatformCommandException(
                            "Platform coordinate must include a version. Use `group:artifact:version`."));
                    String platform = parsed.groupId() + ":" + parsed.artifactId();
                    Path configPath = workingDirectory.resolve("zolt.toml");
                    ProjectConfig config = tomlParser.parse(configPath);
                    ProjectConfig updated = tomlWriter.addPlatform(config, platform, version);
                    tomlWriter.write(configPath, updated);
                    printAddSummary(config, platform, version);
                    if (noResolve) {
                        spec.commandLine().getOut().println("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                        return;
                    }
                    printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
                } catch (PlatformCommandException
                        | ArtifactCacheException
                        | CoordinateParseException
                        | ResolveException
                        | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }

            private void printAddSummary(ProjectConfig original, String platform, String version) {
                String existing = original.platforms().get(platform);
                if (version.equals(existing)) {
                    spec.commandLine().getOut().println("Platform " + platform + ":" + version
                            + " already exists in [platforms]");
                } else if (existing != null) {
                    spec.commandLine().getOut().println("Updated platform " + platform
                            + " from " + existing + " to " + version + " in [platforms]");
                } else {
                    spec.commandLine().getOut().println("Added platform " + platform + ":" + version
                            + " to [platforms]");
                }
            }
        }

        @Command(
                name = "remove",
                mixinStandardHelpOptions = true,
                description = "Remove a platform BOM import and refresh zolt.lock.")
        public static final class RemoveCommand implements Runnable {
            @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT", description = "Platform BOM coordinate.")
            private String coordinate;

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
                    Coordinate parsed = coordinateParser.parse(coordinate);
                    if (parsed.version().isPresent()) {
                        throw new PlatformCommandException(
                                "Platform remove coordinate must not include a version. Use `group:artifact`.");
                    }
                    String platform = parsed.groupId() + ":" + parsed.artifactId();
                    Path configPath = workingDirectory.resolve("zolt.toml");
                    ProjectConfig config = tomlParser.parse(configPath);
                    if (!config.platforms().containsKey(platform)) {
                        spec.commandLine().getOut().println(
                                "Platform " + platform + " is not present in [platforms]; nothing to remove.");
                        return;
                    }
                    ProjectConfig updated = tomlWriter.removePlatform(config, platform);
                    tomlWriter.write(configPath, updated);
                    spec.commandLine().getOut().println("Removed platform " + platform + " from [platforms]");
                    printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
                } catch (PlatformCommandException
                        | ArtifactCacheException
                        | CoordinateParseException
                        | ResolveException
                        | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
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
                String section = sectionName(request.section());
                if (!hasDependency(config, request.section(), request.coordinate())) {
                    spec.commandLine().getOut().println("Dependency " + request.coordinate()
                            + " is not present in [" + section + "]; nothing to remove.");
                    return;
                }
                ProjectConfig updated = tomlWriter.removeDependency(config, request.section(), request.coordinate());
                tomlWriter.write(configPath, updated);
                spec.commandLine().getOut().println(
                        "Removed dependency " + request.coordinate() + " from [" + section + "]");
                printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
            } catch (RemoveCommandException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
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
        @Option(names = "--locked", description = "Fail if zolt.lock would change.")
        private boolean locked;

        @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
        private boolean offline;

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
                        cacheRoot,
                        locked,
                        offline);
                printResolveResult(spec, result, !locked);
            } catch (ArtifactCacheException | ResolveException | ZoltConfigException exception) {
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
        @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
        private boolean offline;

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
                BuildResult result = new BuildService().build(workingDirectory, config, cacheRoot, offline);
                if (result.resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println("Compiled " + result.sourceCount() + " main source files");
                spec.commandLine().getOut().println("Wrote classes to " + result.outputDirectory());
            } catch (BuildException
                    | ArtifactCacheException
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
                RunResult result = new RunService().run(
                        workingDirectory,
                        config,
                        cacheRoot,
                        arguments,
                        output -> {
                            spec.commandLine().getOut().print(output);
                            spec.commandLine().getOut().flush();
                        });
                String output = result.javaRunResult().output();
                if (!output.isEmpty() && !output.endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().println("Ran " + result.javaRunResult().mainClass());
            } catch (JavaRunException exception) {
                spec.commandLine().getErr().println("error: " + firstLine(exception.getMessage()));
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } catch (BuildException
                    | JavacException
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
                    spec.commandLine().getOut().println("Run with dependencies: zolt run-package -- [args]");
                } else {
                    spec.commandLine().getOut().println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
                }
                spec.commandLine().getOut().println("Thin jar: dependencies are not bundled.");
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

    @Command(name = "run-package", description = "Run a packaged thin jar with runtime dependencies.")
    public static final class RunPackageCommand implements Runnable {
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
                RunPackageResult result = new RunPackageService().runPackage(
                        workingDirectory,
                        config,
                        cacheRoot,
                        arguments);
                String output = result.javaRunResult().output();
                printAndFlush(spec, output);
                if (!output.isEmpty() && !output.endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().println("Ran packaged "
                        + result.javaRunResult().mainClass()
                        + " from "
                        + result.packageResult().jarPath());
            } catch (BuildException
                    | JavacException
                    | JavaRunException
                    | ManifestGenerationException
                    | PackageException
                    | ResourceCopyException
                    | RunPackageException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "native", description = "Build a native binary with GraalVM Native Image.")
    public static final class NativeCommand implements Runnable {
        @Option(names = "--native-image", description = "Path to the native-image executable.")
        private Path nativeImageExecutable;

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
                NativeBuildResult result = new NativeBuildService().buildNative(
                        workingDirectory,
                        config,
                        cacheRoot,
                        nativeImageExecutable);
                if (result.packageResult().buildResult().resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println("Built native binary at "
                        + result.nativeImageResult().outputBinary());
                spec.commandLine().getOut().println("Native Image log written to "
                        + result.nativeImageResult().logFile());
            } catch (BuildException
                    | JavacException
                    | ManifestGenerationException
                    | NativeImageException
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

    @Command(name = "release-archive", description = "Assemble a release archive from a native binary.")
    public static final class ReleaseArchiveCommand implements Runnable {
        @Option(names = "--target", description = "Release target. Supported: macos-arm64, macos-x64, linux-x64, windows-x64.")
        private String target;

        @Option(names = "--binary", description = "Path to the native binary to archive.")
        private Path binary;

        @Option(names = "--output", description = "Directory for release archives.")
        private Path outputDirectory = Path.of("dist");

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                ReleaseTarget releaseTarget = target == null ? ReleaseTarget.current() : ReleaseTarget.fromId(target);
                Path nativeBinary = binary == null
                        ? defaultNativeBinary(config, releaseTarget)
                        : binary;
                ReleaseArchiveResult result = new ReleaseArchiveService().assemble(
                        workingDirectory,
                        config,
                        releaseTarget,
                        nativeBinary,
                        outputDirectory);
                spec.commandLine().getOut().println("Assembled " + result.target().id() + " release archive");
                spec.commandLine().getOut().println("Included " + result.fileCount() + " files under " + result.rootDirectory());
                spec.commandLine().getOut().println("Wrote archive to " + result.archivePath());
            } catch (ReleaseArchiveException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private static Path defaultNativeBinary(ProjectConfig config, ReleaseTarget target) {
            String imageName = config.nativeSettings()
                    .withDefaultImageName(config.project().name())
                    .imageName();
            String binaryName = target == ReleaseTarget.WINDOWS_X64 && !imageName.endsWith(".exe")
                    ? imageName + ".exe"
                    : imageName;
            return Path.of(config.nativeSettings().output()).resolve(binaryName);
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
        printResolveResult(spec, result, true);
    }

    private static void printResolveResult(CommandSpec spec, ResolveResult result, boolean wroteLockfile) {
        spec.commandLine().getOut().println("Resolved " + result.resolvedCount() + " packages");
        spec.commandLine().getOut().println("Downloaded " + result.downloadCount() + " artifacts");
        spec.commandLine().getOut().println("Conflicts " + result.conflictCount());
        if (wroteLockfile) {
            spec.commandLine().getOut().println("Wrote " + result.lockfilePath());
        } else {
            spec.commandLine().getOut().println("Verified " + result.lockfilePath());
        }
    }

    private static void printAndFlush(CommandSpec spec, String output) {
        spec.commandLine().getOut().print(output);
        spec.commandLine().getOut().flush();
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
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

    private record AddRequest(DependencySection section, String coordinate, String version, boolean managed) {
    }

    private record RemoveRequest(DependencySection section, String coordinate) {
    }

    private static Map<String, String> dependencies(ProjectConfig config, DependencySection section) {
        return section == DependencySection.TEST ? config.testDependencies() : config.dependencies();
    }

    private static java.util.Set<String> managedDependencies(ProjectConfig config, DependencySection section) {
        return section == DependencySection.TEST ? config.managedTestDependencies() : config.managedDependencies();
    }

    private static boolean hasDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return dependencies(config, section).containsKey(coordinate)
                || managedDependencies(config, section).contains(coordinate);
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

    private static final class PlatformCommandException extends RuntimeException {
        private PlatformCommandException(String message) {
            super(message);
        }
    }
}
