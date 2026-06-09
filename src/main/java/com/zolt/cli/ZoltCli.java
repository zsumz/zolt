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
import com.zolt.doctor.SelfHostingCheckResult;
import com.zolt.doctor.SelfHostingCheckService;
import com.zolt.ide.IdeModelJsonWriter;
import com.zolt.ide.IdeModelService;
import com.zolt.ide.WorkspaceIdeModelJsonWriter;
import com.zolt.ide.WorkspaceIdeModelService;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.perf.TimingFormat;
import com.zolt.perf.TimingFormatter;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.DependencySection;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusAugmentationResult;
import com.zolt.quarkus.QuarkusAugmentationRequestFactory;
import com.zolt.quarkus.QuarkusBuildAugmentationService;
import com.zolt.quarkus.QuarkusPlan;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.QuarkusPlanFormatter;
import com.zolt.quarkus.QuarkusPlanService;
import com.zolt.quarkus.QuarkusTestPlan;
import com.zolt.quarkus.QuarkusTestPlanFormatter;
import com.zolt.quarkus.QuarkusTestPlanService;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.release.ReleaseArchiveException;
import com.zolt.release.ReleaseArchiveResult;
import com.zolt.release.ReleaseArchiveService;
import com.zolt.release.ReleaseTarget;
import com.zolt.release.ReleaseVerificationException;
import com.zolt.release.ReleaseVerificationResult;
import com.zolt.release.ReleaseVerificationService;
import com.zolt.selfhost.NativeSmokeException;
import com.zolt.selfhost.NativeSmokeResult;
import com.zolt.selfhost.NativeSmokeService;
import com.zolt.selfhost.SelfCheckResult;
import com.zolt.selfhost.SelfCheckService;
import com.zolt.selfhost.SelfHostingParityException;
import com.zolt.selfhost.SelfHostingParityResult;
import com.zolt.selfhost.SelfHostingParityService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.tree.DependencyTreeFormatter;
import com.zolt.tree.DependencyWhyException;
import com.zolt.tree.DependencyWhyFormatter;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspacePackageResult;
import com.zolt.workspace.WorkspacePackageService;
import com.zolt.workspace.WorkspaceResolveService;
import com.zolt.workspace.WorkspaceRunResult;
import com.zolt.workspace.WorkspaceRunService;
import com.zolt.workspace.WorkspaceRunPackageResult;
import com.zolt.workspace.WorkspaceRunPackageService;
import com.zolt.workspace.WorkspaceSelectionRequest;
import com.zolt.workspace.WorkspaceTestResult;
import com.zolt.workspace.WorkspaceTestService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
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
                ZoltCli.IdeCommand.class,
                ZoltCli.QuarkusCommand.class,
                ZoltCli.BuildCommand.class,
                ZoltCli.RunCommand.class,
                ZoltCli.TestCommand.class,
                ZoltCli.PackageCommand.class,
                ZoltCli.RunPackageCommand.class,
                ZoltCli.NativeCommand.class,
                ZoltCli.NativeSmokeCommand.class,
                ZoltCli.ReleaseArchiveCommand.class,
                ZoltCli.ReleaseVerifyCommand.class,
                ZoltCli.SelfCheckCommand.class,
                ZoltCli.SelfParityCommand.class,
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

    public static final class TimingOptions {
        @Option(names = "--timings", description = "Print command timing information.")
        private boolean enabled;

        @Option(names = "--timings-format", description = "Timing output format: text or json.")
        private TimingFormat format = TimingFormat.TEXT;
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
                paramLabel = "[api|runtime|provided|dev|test|processor|test-processor] GROUP:ARTIFACT[:VERSION]",
                description = "Dependency coordinate, optionally prefixed with a dependency section.")
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
                    | DependencySectionException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private AddRequest parseRequest(List<String> values) {
            DependencySection section = parseSection(values, "zolt add");
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
            Map<String, String> dependencies = dependencies(original, request.section());
            String section = sectionName(request.section());
            String existing = dependencies.get(request.coordinate());
            String existingWorkspace = workspaceDependencies(original, request.section()).get(request.coordinate());
            String conflicting = conflictingDependencies(original, request.section()).get(request.coordinate());
            String conflictingWorkspace = conflictingWorkspaceDependencies(original, request.section()).get(request.coordinate());
            boolean existingManaged = managedDependencies(original, request.section()).contains(request.coordinate());
            boolean conflictingManaged = conflictingManagedDependencies(original, request.section()).contains(request.coordinate());
            if (request.managed()) {
                if (existingManaged) {
                    spec.commandLine().getOut().println("Dependency " + request.coordinate()
                            + " already uses a platform-managed version in [" + section + "]");
                } else if (existing != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from " + existing + " to platform-managed version in [" + section + "]");
                } else if (existingWorkspace != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from workspace member " + existingWorkspace
                            + " to platform-managed version in [" + section + "]");
                } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from " + existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                            + " to platform-managed version in [" + section + "]");
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
            } else if (existingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from workspace member " + existingWorkspace
                        + " to " + request.version() + " in [" + section + "]");
            } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                        + " to " + request.version() + " in [" + section + "]");
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
                paramLabel = "[api|runtime|provided|dev|test|processor|test-processor] GROUP:ARTIFACT",
                description = "Dependency coordinate, optionally prefixed with a dependency section.")
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
                    | DependencySectionException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private RemoveRequest parseRequest(List<String> values) {
            DependencySection section = parseSection(values, "zolt remove");
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

        @Option(names = "--workspace", description = "Resolve the discovered workspace and write the root zolt.lock.")
        private boolean workspace;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                if (workspace) {
                    ResolveResult result = timings.measure(
                            "resolve workspace",
                            () -> new WorkspaceResolveService().resolve(
                                    workingDirectory,
                                    cacheRoot,
                                    locked,
                                    offline),
                            ZoltCli::resolveAttributes);
                    printResolveResult(spec, result, !locked);
                    return;
                }
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                ResolveResult result = timings.measure(
                        "resolve graph",
                        () -> new ResolveService().resolve(
                                workingDirectory,
                                config,
                                cacheRoot,
                                locked,
                                offline),
                        ZoltCli::resolveAttributes);
                printResolveResult(spec, result, !locked);
            } catch (ArtifactCacheException | ResolveException | WorkspaceConfigException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "resolve", workingDirectory, timingOptions, timings);
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
            COMPILE("compile"),
            RUNTIME("runtime"),
            TEST("test"),
            PROCESSOR("processor"),
            TEST_PROCESSOR("test-processor"),
            QUARKUS_DEPLOYMENT("quarkus-deployment");

            private final String label;

            Kind(String label) {
                this.label = label;
            }

            private static Kind parse(String value) {
                for (Kind kind : values()) {
                    if (kind.label.equalsIgnoreCase(value)) {
                        return kind;
                    }
                }
                throw new ClasspathCommandException(
                        "Unknown classpath kind `" + value
                                + "`. Use compile, runtime, test, processor, test-processor, or quarkus-deployment.");
            }
        }

        @Parameters(
                index = "0",
                paramLabel = "compile|runtime|test|processor|test-processor|quarkus-deployment",
                description = "Classpath kind to print.")
        private String kind;

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
                String output = new ClasspathFormatter().format(switch (Kind.parse(kind)) {
                    case COMPILE -> classpaths.compile();
                    case RUNTIME -> classpaths.runtime();
                    case TEST -> classpaths.test();
                    case PROCESSOR -> classpaths.processor();
                    case TEST_PROCESSOR -> classpaths.testProcessor();
                    case QUARKUS_DEPLOYMENT -> classpaths.quarkusDeployment();
                });
                printAndFlush(spec, output);
            } catch (ClasspathCommandException | LockfileReadException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(
            name = "ide",
            mixinStandardHelpOptions = true,
            description = "Export project models for IDE and tooling integrations.",
            subcommands = {
                    IdeCommand.ModelCommand.class
            })
    public static final class IdeCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }

        @Command(name = "model", description = "Export the Zolt project model.")
        public static final class ModelCommand implements Runnable {
            enum Format {
                JSON
            }

            @Option(names = "--format", required = true, description = "Output format: json.")
            private Format format;

            @Option(names = "--check-lock", description = "Report whether zolt.lock is stale without rewriting it.")
            private boolean checkLock;

            @Option(names = "--offline", description = "Use only artifacts already present in the local cache when checking zolt.lock.")
            private boolean offline;

            @Option(names = "--workspace", description = "Export the discovered workspace model.")
            private boolean workspace;

            @Option(names = "--cwd", hidden = true)
            private Path workingDirectory = Path.of(".");

            @Option(names = "--cache-root", hidden = true)
            private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

            @Mixin
            private TimingOptions timingOptions = new TimingOptions();

            @Spec
            private CommandSpec spec;

            @Override
            public void run() {
                TimingRecorder timings = timingRecorder(timingOptions);
                try {
                    if (workspace) {
                        com.zolt.ide.WorkspaceIdeModel model = timings.measure(
                                "ide model export",
                                () -> new WorkspaceIdeModelService().export(
                                        workingDirectory,
                                        cacheRoot,
                                        checkLock,
                                        offline),
                                ZoltCli::workspaceIdeModelAttributes);
                        String output = timings.measure(
                                "ide model json",
                                () -> new WorkspaceIdeModelJsonWriter().write(model));
                        printAndFlush(spec, output);
                        return;
                    }
                    com.zolt.ide.IdeModel model = timings.measure(
                            "ide model export",
                            () -> new IdeModelService().export(
                                    workingDirectory,
                                    cacheRoot,
                                    checkLock,
                                    offline),
                            ZoltCli::ideModelAttributes);
                    String output = timings.measure(
                            "ide model json",
                            () -> new IdeModelJsonWriter().write(model));
                    printAndFlush(spec, output);
                } finally {
                    printTimings(spec, "ide model", workingDirectory, timingOptions, timings);
                }
            }
        }
    }

    @Command(
            name = "quarkus",
            mixinStandardHelpOptions = true,
            description = "Inspect Quarkus build-time augmentation inputs.",
            subcommands = {
                    QuarkusCommand.PlanCommand.class,
                    QuarkusCommand.TestPlanCommand.class
            })
    public static final class QuarkusCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }

        @Command(name = "plan", description = "Print the Quarkus augmentation input plan.")
        public static final class PlanCommand implements Runnable {
            @Option(names = "--cwd", hidden = true)
            private Path workingDirectory = Path.of(".");

            @Option(names = "--cache-root", hidden = true)
            private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

            @Mixin
            private TimingOptions timingOptions = new TimingOptions();

            @Spec
            private CommandSpec spec;

            @Override
            public void run() {
                TimingRecorder timings = timingRecorder(timingOptions);
                try {
                    ProjectConfig config = timings.measure(
                            "config read",
                            () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                    QuarkusPlan plan = timings.measure(
                            "quarkus plan",
                            () -> new QuarkusPlanService().plan(workingDirectory, config, cacheRoot),
                            ZoltCli::quarkusPlanAttributes);
                    String output = timings.measure(
                            "quarkus plan format",
                            () -> new QuarkusPlanFormatter().format(plan));
                    printAndFlush(spec, output);
                    timings.measure(
                            "quarkus augmentation request",
                            () -> new QuarkusAugmentationRequestFactory().create(plan));
                } catch (LockfileReadException | QuarkusPlanException | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                } finally {
                    printTimings(spec, "quarkus plan", workingDirectory, timingOptions, timings);
                }
            }
        }

        @Command(name = "test-plan", description = "Print the Quarkus test bootstrap plan.")
        public static final class TestPlanCommand implements Runnable {
            @Option(names = "--cwd", hidden = true)
            private Path workingDirectory = Path.of(".");

            @Spec
            private CommandSpec spec;

            @Override
            public void run() {
                try {
                    ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                    QuarkusTestPlan plan = new QuarkusTestPlanService().plan(workingDirectory, config);
                    printAndFlush(spec, new QuarkusTestPlanFormatter().format(plan));
                } catch (QuarkusPlanException | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }
        }
    }

    @Command(name = "build", description = "Compile main Java sources with the resolved compile classpath.")
    public static final class BuildCommand implements Runnable {
        @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
        private boolean offline;

        @Option(names = "--workspace", description = "Build workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                if (workspace) {
                    WorkspaceBuildResult result = timings.measure(
                            "build workspace",
                            () -> new WorkspaceBuildService().build(
                                    workingDirectory,
                                    cacheRoot,
                                    offline,
                                    workspaceSelection(all, members, memberGroups)),
                            ZoltCli::workspaceBuildAttributes);
                    if (result.resolvedLockfile()) {
                        spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
                    }
                    for (WorkspaceBuildResult.MemberBuildResult member : result.members()) {
                        spec.commandLine().getOut().println(
                                "Compiled "
                                        + member.result().sourceCount()
                                        + " main source files in "
                                        + member.member());
                    }
                    spec.commandLine().getOut().println("Compiled " + result.sourceCount() + " workspace main source files");
                    return;
                }
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                BuildResult result = timings.measure(
                        "compile main",
                        () -> new BuildService().build(workingDirectory, config, cacheRoot, offline),
                        ZoltCli::buildAttributes);
                if (result.resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println("Compiled " + result.sourceCount() + " main source files");
                spec.commandLine().getOut().println("Wrote classes to " + result.outputDirectory());
                Optional<QuarkusAugmentationResult> quarkusResult =
                        timings.measure(
                                "quarkus augmentation",
                                () -> new QuarkusBuildAugmentationService().augmentIfEnabled(
                                        workingDirectory,
                                        config,
                                        cacheRoot),
                                ZoltCli::quarkusAugmentationAttributes);
                if (quarkusResult.isPresent()) {
                    spec.commandLine().getOut().println(
                            "Ran Quarkus augmentation; runner jar "
                                    + quarkusResult.orElseThrow().workerResult().runnerJar());
                }
            } catch (BuildException
                    | ArtifactCacheException
                    | JavacException
                    | QuarkusAugmentationException
                    | QuarkusPlanException
                    | ResourceCopyException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | WorkspaceConfigException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "build", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(name = "run", description = "Build and run the configured main class.")
    public static final class RunCommand implements Runnable {
        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed to the application after `--`.")
        private List<String> arguments = List.of();

        @Option(names = "--workspace", description = "Run workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                if (workspace) {
                    WorkspaceRunResult result = new WorkspaceRunService().run(
                            workingDirectory,
                            cacheRoot,
                            workspaceSelection(all, members, memberGroups),
                            arguments,
                            output -> {
                                spec.commandLine().getOut().print(output);
                                spec.commandLine().getOut().flush();
                            });
                    if (result.resolvedLockfile()) {
                        spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
                    }
                    for (WorkspaceRunResult.MemberRunResult member : result.members()) {
                        String output = member.result().javaRunResult().output();
                        if (!output.isEmpty() && !output.endsWith("\n")) {
                            spec.commandLine().getOut().println();
                        }
                        spec.commandLine().getOut().println("Ran "
                                + member.result().javaRunResult().mainClass()
                                + " in "
                                + member.member());
                    }
                    return;
                }
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
                    | QuarkusAugmentationException
                    | QuarkusPlanException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | WorkspaceConfigException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "test", description = "Compile and run tests, starting with JUnit support.")
    public static final class TestCommand implements Runnable {
        @Option(names = "--workspace", description = "Test workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                if (workspace) {
                    WorkspaceTestResult result = timings.measure(
                            "test workspace",
                            () -> new WorkspaceTestService().test(
                                    workingDirectory,
                                    cacheRoot,
                                    workspaceSelection(all, members, memberGroups)),
                            ZoltCli::workspaceTestAttributes);
                    if (result.resolvedLockfile()) {
                        spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
                    }
                    for (WorkspaceTestResult.MemberTestRunResult member : result.members()) {
                        printAndFlush(spec, member.result().output());
                        if (!member.result().output().isEmpty() && !member.result().output().endsWith("\n")) {
                            spec.commandLine().getOut().println();
                        }
                        spec.commandLine().getOut().println("Tests passed in " + member.member());
                    }
                    spec.commandLine().getOut().println(
                            "Tests passed for "
                                    + result.members().size()
                                    + " workspace members");
                    return;
                }
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                TestRunResult result = timings.measure(
                        "run tests",
                        () -> new TestRunService().runTests(workingDirectory, config, cacheRoot),
                        ZoltCli::testRunAttributes);
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
                    | WorkspaceConfigException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "test", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(name = "package", description = "Package compiled classes into a jar.")
    public static final class PackageCommand implements Runnable {
        @Option(names = "--workspace", description = "Package workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--mode", description = "Package mode: thin, spring-boot, quarkus, or uber.")
        private String mode;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                Optional<PackageMode> packageModeOverride = packageModeOverride(mode);
                if (workspace) {
                    WorkspacePackageResult result = timings.measure(
                            "package workspace",
                            () -> new WorkspacePackageService().packageJars(
                                    workingDirectory,
                                    cacheRoot,
                                    workspaceSelection(all, members, memberGroups),
                                    packageModeOverride),
                            ZoltCli::workspacePackageAttributes);
                    if (result.resolvedLockfile()) {
                        spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
                    }
                    for (WorkspacePackageResult.MemberPackageResult member : result.members()) {
                        spec.commandLine().getOut().println(
                                packageSummary(member.result())
                                        + " in "
                                        + member.member());
                        if (member.result().hasMainClass()) {
                            spec.commandLine().getOut().println("Included Main-Class manifest entry in " + member.member());
                        }
                        spec.commandLine().getOut().println("Wrote jar to " + member.result().jarPath());
                    }
                    spec.commandLine().getOut().println(
                            "Packaged "
                                    + result.members().size()
                                    + " workspace members");
                    return;
                }
                ProjectConfig config = withPackageModeOverride(
                        timings.measure(
                                "config read",
                                () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"))),
                        packageModeOverride);
                PackageResult result = timings.measure(
                        "package",
                        () -> new PackageService().packageJar(workingDirectory, config, cacheRoot),
                        ZoltCli::packageAttributes);
                if (result.buildResult().resolvedLockfile()) {
                    spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
                }
                spec.commandLine().getOut().println(packageSummary(result));
                if (result.hasMainClass()) {
                    spec.commandLine().getOut().println("Included Main-Class manifest entry");
                    spec.commandLine().getOut().println("Run with: java -jar " + result.jarPath());
                    if (result.mode() == PackageMode.SPRING_BOOT) {
                        spec.commandLine().getOut().println("Run with Zolt: zolt run-package --mode spring-boot -- [args]");
                    } else if (result.mode() == PackageMode.QUARKUS) {
                        spec.commandLine().getOut().println("Run with Zolt: zolt run");
                    } else {
                        spec.commandLine().getOut().println("Run with dependencies: zolt run-package -- [args]");
                    }
                } else {
                    spec.commandLine().getOut().println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
                }
                if (result.mode() == PackageMode.SPRING_BOOT) {
                    spec.commandLine().getOut().println("Spring Boot jar: dependencies are nested under BOOT-INF/lib.");
                } else if (result.mode() == PackageMode.QUARKUS) {
                    spec.commandLine().getOut().println("Quarkus fast-jar: deploy the whole target/quarkus-app directory.");
                } else {
                    spec.commandLine().getOut().println("Thin jar: dependencies are not bundled.");
                    result.runtimeClasspathPath().ifPresent(path ->
                            spec.commandLine().getOut().println("Wrote runtime classpath to " + path));
                }
                spec.commandLine().getOut().println("Wrote jar to " + result.jarPath());
            } catch (BuildException
                    | JavacException
                    | ManifestGenerationException
                    | PackageException
                    | ResourceCopyException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | WorkspaceConfigException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "package", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(name = "run-package", description = "Run a packaged thin jar with runtime dependencies.")
    public static final class RunPackageCommand implements Runnable {
        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed to the application after `--`.")
        private List<String> arguments = List.of();

        @Option(names = "--workspace", description = "Run packaged workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--mode", description = "Package mode: thin, spring-boot, quarkus, or uber.")
        private String mode;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                Optional<PackageMode> packageModeOverride = packageModeOverride(mode);
                if (workspace) {
                    WorkspaceRunPackageResult result = new WorkspaceRunPackageService().runPackages(
                            workingDirectory,
                            cacheRoot,
                            workspaceSelection(all, members, memberGroups),
                            arguments,
                            packageModeOverride);
                    if (result.resolvedLockfile()) {
                        spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
                    }
                    for (WorkspaceRunPackageResult.MemberRunPackageResult member : result.members()) {
                        String output = member.result().javaRunResult().output();
                        printAndFlush(spec, output);
                        if (!output.isEmpty() && !output.endsWith("\n")) {
                            spec.commandLine().getOut().println();
                        }
                        spec.commandLine().getOut().println("Ran packaged "
                                + member.result().javaRunResult().mainClass()
                                + " in "
                                + member.member()
                                + " from "
                                + member.result().packageResult().jarPath());
                    }
                    return;
                }
                ProjectConfig config = withPackageModeOverride(
                        new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")),
                        packageModeOverride);
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
                    | WorkspaceConfigException
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

    @Command(name = "native-smoke", description = "Smoke a native Zolt binary against real workflows.")
    public static final class NativeSmokeCommand implements Runnable {
        @Option(names = "--binary", required = true, description = "Native Zolt binary to smoke.")
        private Path binary;

        @Option(names = "--work-dir", description = "Directory for native smoke work.")
        private Path workDirectory = Path.of("target/native-smoke");

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                NativeSmokeResult result = new NativeSmokeService().smoke(
                        workingDirectory,
                        config,
                        binary,
                        workDirectory);
                spec.commandLine().getOut().println("Native smoke status: ok");
                spec.commandLine().getOut().println("Smoked binary " + result.binary());
                spec.commandLine().getOut().println("Verified release archive " + result.archive());
                spec.commandLine().getOut().println("Ran generated project " + result.projectDirectory());
            } catch (NativeSmokeException | ZoltConfigException exception) {
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
                spec.commandLine().getOut().println("Wrote checksum to " + result.checksumPath());
                spec.commandLine().getOut().println("Wrote manifest to " + result.manifestPath());
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

    @Command(name = "release-verify", description = "Verify release archives by unpacking and smoking the binary.")
    public static final class ReleaseVerifyCommand implements Runnable {
        @Parameters(arity = "1..*", paramLabel = "ARCHIVE", description = "Release archive path to verify.")
        private List<Path> archives;

        @Option(names = "--work-dir", description = "Directory for unpacked verification work.")
        private Path workDirectory = Path.of("target/release-verify");

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                List<Path> resolvedArchives = archives.stream()
                        .map(path -> workingDirectory.resolve(path).normalize())
                        .toList();
                ReleaseVerificationResult result = new ReleaseVerificationService().verify(
                        resolvedArchives,
                        workingDirectory.resolve(workDirectory).normalize(),
                        config.project().version());
                for (ReleaseVerificationResult.VerifiedArchive archive : result.archives()) {
                    spec.commandLine().getOut().println("Verified release archive " + archive.archivePath());
                    spec.commandLine().getOut().println("Unpacked to " + archive.unpackDirectory());
                    spec.commandLine().getOut().println("Ran smoke binary " + archive.binaryPath());
                }
                spec.commandLine().getOut().println("Verified " + result.verifiedCount() + " release archives");
            } catch (ReleaseVerificationException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "self-check", description = "Run the self-hosting verification path.")
    public static final class SelfCheckCommand implements Runnable {
        @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
        private boolean offline;

        @Option(names = "--native", description = "Also build and smoke the Native Image binary.")
        private boolean nativeCheck;

        @Option(names = "--native-image", description = "Path to the native-image executable.")
        private Path nativeImageExecutable;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                SelfCheckResult result = timings.measure(
                        "self-check",
                        () -> new SelfCheckService().check(
                                workingDirectory,
                                cacheRoot,
                                offline,
                                nativeCheck,
                                nativeImageExecutable),
                        ZoltCli::selfCheckAttributes);
                printSelfCheckStatus(spec, result);
                if (!result.ok()) {
                    throw new CommandLine.ExecutionException(spec.commandLine(), "Self-check failed.");
                }
            } finally {
                printTimings(spec, "self-check", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(name = "self-parity", description = "Compare bootstrap and Zolt-built jar entries.")
    public static final class SelfParityCommand implements Runnable {
        @Option(names = "--bootstrap-jar", required = true, description = "Bootstrap-built jar to compare against.")
        private Path bootstrapJar;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                SelfHostingParityResult result = new SelfHostingParityService()
                        .compare(workingDirectory, cacheRoot, bootstrapJar);
                if (!result.ok()) {
                    spec.commandLine().getErr().println("error: Self-hosting parity failed: bootstrap jar and Zolt-built jar contents differ.");
                    spec.commandLine().getErr().println("Missing from Zolt-built jar:");
                    spec.commandLine().getErr().print(formatEntries(result.missingFromZolt()));
                    spec.commandLine().getErr().println("Extra in Zolt-built jar:");
                    spec.commandLine().getErr().print(formatEntries(result.extraInZolt()));
                    throw new CommandLine.ExecutionException(spec.commandLine(), "Self-hosting parity failed.");
                }
                spec.commandLine().getOut().println("Self-hosting parity status: ok");
                spec.commandLine().getOut().println("Bootstrap jar: " + result.bootstrapJar());
                spec.commandLine().getOut().println("Zolt-built jar: " + result.zoltJar());
                spec.commandLine().getOut().println("Jar entries match");
            } catch (BuildException
                    | JavacException
                    | ManifestGenerationException
                    | PackageException
                    | ResourceCopyException
                    | SelfHostingParityException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }

        private static String formatEntries(Set<String> entries) {
            if (entries.isEmpty()) {
                return "  <none>\n";
            }
            StringBuilder output = new StringBuilder();
            entries.stream()
                    .limit(50)
                    .forEach(entry -> output.append("  - ").append(entry).append('\n'));
            if (entries.size() > 50) {
                output.append("  ... ").append(entries.size() - 50).append(" more\n");
            }
            return output.toString();
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
                CleanResult result = new CleanService().clean(workingDirectory, config);
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
        @Option(names = "--self-hosting", description = "Check whether the project is ready for Zolt-owned self-hosting flows.")
        private boolean selfHosting;

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
                boolean ok = status.ok();
                if (selfHosting) {
                    SelfHostingCheckResult result = new SelfHostingCheckService().check(workingDirectory);
                    printSelfHostingStatus(spec, result);
                    ok = ok && result.ok();
                }
                if (!ok) {
                    throw new CommandLine.ExecutionException(spec.commandLine(), "Project health check failed.");
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

    private static TimingRecorder timingRecorder(TimingOptions options) {
        return new TimingRecorder(options != null && options.enabled);
    }

    private static void printTimings(
            CommandSpec spec,
            String command,
            Path projectRoot,
            TimingOptions options,
            TimingRecorder recorder) {
        if (options == null || !options.enabled || recorder.events().isEmpty()) {
            return;
        }
        spec.commandLine().getErr().print(TimingFormatter.format(
                options.format,
                command,
                projectRoot,
                recorder.events()));
        spec.commandLine().getErr().flush();
    }

    private static Map<String, String> resolveAttributes(ResolveResult result) {
        return Map.of(
                "resolvedPackages", Integer.toString(result.resolvedCount()),
                "downloadedArtifacts", Integer.toString(result.downloadCount()),
                "conflicts", Integer.toString(result.conflictCount()));
    }

    private static Map<String, String> buildAttributes(BuildResult result) {
        return Map.of(
                "sourceFiles", Integer.toString(result.sourceCount()),
                "resourceFiles", Integer.toString(result.resourceCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> workspaceBuildAttributes(WorkspaceBuildResult result) {
        return Map.of(
                "members", Integer.toString(result.members().size()),
                "sourceFiles", Integer.toString(result.sourceCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> testRunAttributes(TestRunResult result) {
        return Map.of(
                "mainSourceFiles", Integer.toString(result.compileResult().buildResult().sourceCount()),
                "testSourceFiles", Integer.toString(result.compileResult().sourceCount()),
                "testResourceFiles", Integer.toString(result.compileResult().resourceCount()),
                "outputBytes", Integer.toString(result.output().length()));
    }

    private static Map<String, String> workspaceTestAttributes(WorkspaceTestResult result) {
        return Map.of(
                "members", Integer.toString(result.members().size()),
                "mainSourceFiles", Integer.toString(result.mainSourceCount()),
                "testSourceFiles", Integer.toString(result.testSourceCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> packageAttributes(PackageResult result) {
        return Map.of(
                "mode", result.mode().configValue(),
                "entries", Integer.toString(result.entryCount()),
                "hasMainClass", Boolean.toString(result.hasMainClass()),
                "resolvedLockfile", Boolean.toString(result.buildResult().resolvedLockfile()));
    }

    private static Map<String, String> workspacePackageAttributes(WorkspacePackageResult result) {
        return Map.of(
                "members", Integer.toString(result.members().size()),
                "entries", Integer.toString(result.entryCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> quarkusAugmentationAttributes(Optional<QuarkusAugmentationResult> result) {
        if (result.isEmpty()) {
            return Map.of("enabled", "false");
        }
        QuarkusAugmentationResult augmentation = result.orElseThrow();
        return Map.of(
                "enabled", "true",
                "runnerJar", augmentation.workerResult().runnerJar().toString());
    }

    private static Map<String, String> quarkusPlanAttributes(QuarkusPlan plan) {
        return Map.of(
                "runtimeClasspathEntries", Integer.toString(plan.runtimeClasspath().size()),
                "deploymentClasspathEntries", Integer.toString(plan.deploymentClasspath().size()),
                "extensions", Integer.toString(plan.extensions().size()),
                "packageMode", plan.packageMode().configValue());
    }

    private static Map<String, String> ideModelAttributes(com.zolt.ide.IdeModel model) {
        java.util.LinkedHashMap<String, String> attributes = new java.util.LinkedHashMap<>();
        attributes.put("sourceRoots", Integer.toString(model.sourceRoots().size()));
        attributes.put("resourceRoots", Integer.toString(model.resourceRoots().size()));
        attributes.put("compileClasspathEntries", Integer.toString(model.classpaths().compile().size()));
        attributes.put("runtimeClasspathEntries", Integer.toString(model.classpaths().runtime().size()));
        attributes.put("testClasspathEntries", Integer.toString(model.classpaths().test().size()));
        attributes.put("diagnostics", Integer.toString(model.diagnostics().size()));
        return attributes;
    }

    private static Map<String, String> workspaceIdeModelAttributes(com.zolt.ide.WorkspaceIdeModel model) {
        return Map.of(
                "projects", Integer.toString(model.projects().size()),
                "edges", Integer.toString(model.edges().size()),
                "diagnostics", Integer.toString(model.diagnostics().size()));
    }

    private static Map<String, String> selfCheckAttributes(SelfCheckResult result) {
        long failedSteps = result.steps().stream().filter(step -> !step.ok()).count();
        return Map.of(
                "steps", Integer.toString(result.steps().size()),
                "failedSteps", Long.toString(failedSteps),
                "ok", Boolean.toString(result.ok()));
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

    private static Optional<PackageMode> packageModeOverride(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PackageMode.fromConfigValue(value).orElseThrow(() -> new PackageException(
                "Unsupported package mode `"
                        + value
                        + "`. Supported package modes are: "
                        + PackageMode.supportedValues()
                        + ".")));
    }

    private static String packageSummary(PackageResult result) {
        if (result.mode() == PackageMode.QUARKUS) {
            return "Packaged Quarkus fast-jar layout with " + result.entryCount() + " files";
        }
        return "Packaged "
                + result.entryCount()
                + " compiled files as "
                + result.mode().configValue()
                + " jar";
    }

    private static ProjectConfig withPackageModeOverride(
            ProjectConfig config,
            Optional<PackageMode> packageModeOverride) {
        return packageModeOverride
                .map(mode -> config.withPackageSettings(new PackageSettings(mode)))
                .orElse(config);
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private static WorkspaceSelectionRequest workspaceSelection(
            boolean all,
            List<String> members,
            List<String> memberGroups) {
        List<String> selectedMembers = new ArrayList<>();
        selectedMembers.addAll(members);
        selectedMembers.addAll(memberGroups);
        return new WorkspaceSelectionRequest(all, selectedMembers);
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

    private static void printSelfHostingStatus(CommandSpec spec, SelfHostingCheckResult result) {
        spec.commandLine().getOut().println("Self-hosting status: " + (result.ok() ? "ok" : "error"));
        for (SelfHostingCheckResult.SelfHostingCheck check : result.checks()) {
            String marker = check.ok() ? "ok" : "error";
            spec.commandLine().getOut().println(marker + ": " + check.name() + " - " + check.message());
        }
    }

    private static void printSelfCheckStatus(CommandSpec spec, SelfCheckResult result) {
        spec.commandLine().getOut().println("Self-check status: " + (result.ok() ? "ok" : "error"));
        for (SelfCheckResult.SelfCheckStep step : result.steps()) {
            String marker = step.ok() ? "ok" : "error";
            spec.commandLine().getOut().println(marker + ": " + step.name() + " - " + step.message());
        }
    }

    private record AddRequest(DependencySection section, String coordinate, String version, boolean managed) {
    }

    private record RemoveRequest(DependencySection section, String coordinate) {
    }

    private static Map<String, String> dependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.dependencies();
            case API -> config.apiDependencies();
            case RUNTIME -> config.runtimeDependencies();
            case PROVIDED -> config.providedDependencies();
            case DEV -> config.devDependencies();
            case TEST -> config.testDependencies();
            case PROCESSOR -> config.annotationProcessors();
            case TEST_PROCESSOR -> config.testAnnotationProcessors();
        };
    }

    private static java.util.Set<String> managedDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.managedDependencies();
            case API -> config.managedApiDependencies();
            case RUNTIME -> config.managedRuntimeDependencies();
            case PROVIDED -> config.managedProvidedDependencies();
            case DEV -> config.managedDevDependencies();
            case TEST -> config.managedTestDependencies();
            case PROCESSOR -> config.managedAnnotationProcessors();
            case TEST_PROCESSOR -> config.managedTestAnnotationProcessors();
        };
    }

    private static Map<String, String> workspaceDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.workspaceDependencies();
            case API -> config.workspaceApiDependencies();
            case TEST -> config.workspaceTestDependencies();
            case RUNTIME, PROVIDED, DEV, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    private static Map<String, String> conflictingDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> combinedDependencies(
                    config.apiDependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case API -> combinedDependencies(
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case RUNTIME -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case PROVIDED -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.devDependencies());
            case DEV -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    private static java.util.Set<String> conflictingManagedDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case API -> combinedManagedDependencies(
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case RUNTIME -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case PROVIDED -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedDevDependencies());
            case DEV -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> java.util.Set.of();
        };
    }

    private static Map<String, String> conflictingWorkspaceDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.workspaceApiDependencies();
            case API -> config.workspaceDependencies();
            case RUNTIME -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case PROVIDED -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case DEV -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    @SafeVarargs
    private static Map<String, String> combinedDependencies(Map<String, String>... candidates) {
        Map<String, String> combined = new java.util.LinkedHashMap<>();
        for (Map<String, String> candidate : candidates) {
            combined.putAll(candidate);
        }
        return combined;
    }

    @SafeVarargs
    private static Set<String> combinedManagedDependencies(Set<String>... candidates) {
        Set<String> combined = new java.util.LinkedHashSet<>();
        for (Set<String> candidate : candidates) {
            combined.addAll(candidate);
        }
        return combined;
    }

    private static String existingDescription(
            String version,
            boolean managed,
            String workspace) {
        if (version != null) {
            return version;
        }
        if (managed) {
            return "managed version";
        }
        return "workspace member " + workspace;
    }

    private static boolean hasDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return dependencies(config, section).containsKey(coordinate)
                || managedDependencies(config, section).contains(coordinate)
                || workspaceDependencies(config, section).containsKey(coordinate);
    }

    private static String sectionName(DependencySection section) {
        return switch (section) {
            case MAIN -> "dependencies";
            case API -> "api.dependencies";
            case RUNTIME -> "runtime.dependencies";
            case PROVIDED -> "provided.dependencies";
            case DEV -> "dev.dependencies";
            case TEST -> "test.dependencies";
            case PROCESSOR -> "annotationProcessors";
            case TEST_PROCESSOR -> "test.annotationProcessors";
        };
    }

    private static DependencySection parseSection(List<String> values, String command) {
        if (values.size() == 1) {
            return DependencySection.MAIN;
        }
        return switch (values.get(0)) {
            case "api" -> DependencySection.API;
            case "runtime" -> DependencySection.RUNTIME;
            case "provided" -> DependencySection.PROVIDED;
            case "dev" -> DependencySection.DEV;
            case "test" -> DependencySection.TEST;
            case "processor" -> DependencySection.PROCESSOR;
            case "test-processor" -> DependencySection.TEST_PROCESSOR;
            default -> throw new DependencySectionException("Unexpected dependency section `" + values.get(0)
                    + "`. Use `" + command + " api group:artifact`, `"
                    + command + " runtime group:artifact`, `"
                    + command + " provided group:artifact`, `"
                    + command + " dev group:artifact`, `"
                    + command + " test group:artifact`, `"
                    + command + " processor group:artifact`, or `"
                    + command + " test-processor group:artifact`.");
        };
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

    private static final class DependencySectionException extends RuntimeException {
        private DependencySectionException(String message) {
            super(message);
        }
    }

    private static final class ClasspathCommandException extends RuntimeException {
        private ClasspathCommandException(String message) {
            super(message);
        }
    }

    private static final class PlatformCommandException extends RuntimeException {
        private PlatformCommandException(String message) {
            super(message);
        }
    }
}
