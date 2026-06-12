package com.zolt.cli;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.BuildService;
import com.zolt.build.CleanException;
import com.zolt.build.CleanResult;
import com.zolt.build.CleanService;
import com.zolt.build.CoverageException;
import com.zolt.build.CoverageReportSettings;
import com.zolt.build.CoverageResult;
import com.zolt.build.CoverageService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.NativeBuildResult;
import com.zolt.build.NativeBuildService;
import com.zolt.build.NativeImageException;
import com.zolt.build.PackageException;
import com.zolt.build.PackageArtifact;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanFormatter;
import com.zolt.build.PackagePlanService;
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
import com.zolt.build.TestCompileResult;
import com.zolt.build.TestCompileResultWithClasspaths;
import com.zolt.build.TestJvmArguments;
import com.zolt.build.TestReportSettings;
import com.zolt.build.TestRunException;
import com.zolt.build.TestRunResult;
import com.zolt.build.TestRunService;
import com.zolt.build.TestSelection;
import com.zolt.build.TestSelectionException;
import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathFormatter;
import com.zolt.classpath.ClasspathLaneAuditFormatter;
import com.zolt.classpath.ClasspathSet;
import com.zolt.conflict.DependencyConflictFormatter;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.doctor.SelfHostingCheckResult;
import com.zolt.doctor.SelfHostingCheckService;
import com.zolt.explain.GradleExplainFormatter;
import com.zolt.explain.GradleInspectionResult;
import com.zolt.explain.GradleStaticProjectInspector;
import com.zolt.explain.MavenExplainFormatter;
import com.zolt.explain.MavenInspectionResult;
import com.zolt.explain.MavenStaticProjectInspector;
import com.zolt.explain.MigrationBlockerReportFormatter;
import com.zolt.explain.MigrationBlockerReports;
import com.zolt.explain.MigrationExplainException;
import com.zolt.explain.MigrationReadinessScorecardFormatter;
import com.zolt.explain.MigrationReadinessScorecards;
import com.zolt.ide.IdeModelJsonWriter;
import com.zolt.ide.IdeModelService;
import com.zolt.ide.WorkspaceIdeModelJsonWriter;
import com.zolt.ide.WorkspaceIdeModelService;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.perf.TimingFormat;
import com.zolt.perf.TimingFormatter;
import com.zolt.perf.TimingRecorder;
import com.zolt.plan.BuildPlan;
import com.zolt.plan.BuildPlanFormatter;
import com.zolt.plan.BuildPlanService;
import com.zolt.plan.PlanTarget;
import com.zolt.policy.DependencyPolicyReport;
import com.zolt.policy.DependencyPolicyReportException;
import com.zolt.policy.DependencyPolicyReportFormatter;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencySection;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.project.VersionAliasRules;
import com.zolt.publish.PublishContext;
import com.zolt.publish.PublishDryRunFormatter;
import com.zolt.publish.PublishDryRunPlan;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishException;
import com.zolt.publish.PublishReleasePolicyService;
import com.zolt.publish.PublishUploadFormatter;
import com.zolt.publish.PublishUploadResult;
import com.zolt.publish.PublishUploadService;
import com.zolt.quality.QualityCheckFormatter;
import com.zolt.quality.QualityCheckContext;
import com.zolt.quality.QualityCheckReport;
import com.zolt.quality.QualityCheckRequest;
import com.zolt.quality.QualityCheckService;
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
import com.zolt.resolve.RepositoryOverlay;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveOptions;
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
import com.zolt.tree.DependencyJsonFormatter;
import com.zolt.tree.DependencyTreeFormatter;
import com.zolt.tree.DependencyWhyException;
import com.zolt.tree.DependencyWhyFormatter;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
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
        version = ZoltCli.VERSION,
        description = "The modern Java build toolkit.",
        subcommands = {
                CommandLine.HelpCommand.class,
                ZoltCli.InitCommand.class,
                ZoltCli.VersionCommand.class,
                ZoltCli.UpdateCommand.class,
                ZoltCli.CheckCommand.class,
                ZoltCli.AddCommand.class,
                ZoltCli.RemoveCommand.class,
                ZoltCli.PlatformCommand.class,
                ZoltCli.ResolveCommand.class,
                ZoltCli.TreeCommand.class,
                ZoltCli.WhyCommand.class,
                ZoltCli.PolicyCommand.class,
                ZoltCli.ConflictsCommand.class,
                ZoltCli.ExplainCommand.class,
                ZoltCli.PlanCommand.class,
                ZoltCli.ClasspathCommand.class,
                ZoltCli.IdeCommand.class,
                ZoltCli.QuarkusCommand.class,
                ZoltCli.BuildCommand.class,
                ZoltCli.RunCommand.class,
                ZoltCli.TestCommand.class,
                ZoltCli.CoverageCommand.class,
                ZoltCli.PackageCommand.class,
                ZoltCli.PublishCommand.class,
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
    }

    @Command(
            name = "version",
            description = "Print the Zolt version.",
            subcommands = {
                    VersionCommand.SetCommand.class,
                    VersionCommand.RemoveCommand.class
            })
    public static final class VersionCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().getOut().println(VERSION);
        }

        @Command(name = "set", description = "Set a version alias in zolt.toml and refresh zolt.lock.")
        public static final class SetCommand implements Runnable {
            @Parameters(index = "0", paramLabel = "ALIAS", description = "Version alias name.")
            private String alias;

            @Parameters(index = "1", paramLabel = "VERSION", description = "Literal version value.")
            private String version;

            @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
            private boolean noResolve;

            @Option(names = "--cwd", hidden = true)
            private Path workingDirectory = Path.of(".");

            @Option(names = "--cache-root", hidden = true)
            private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

            @Spec
            private CommandSpec spec;

            private final ZoltTomlParser tomlParser = new ZoltTomlParser();
            private final ZoltTomlWriter tomlWriter = new ZoltTomlWriter();
            private final ResolveService resolveService = new ResolveService();

            @Override
            public void run() {
                try {
                    String normalizedAlias = validateVersionAlias(alias);
                    String normalizedVersion = validateVersionAliasValue(normalizedAlias, version);
                    Path configPath = workingDirectory.resolve("zolt.toml");
                    ProjectConfig config = tomlParser.parse(configPath);
                    Map<String, String> aliases = new LinkedHashMap<>(config.versionAliases());
                    String previous = aliases.put(normalizedAlias, normalizedVersion);
                    ProjectConfig updated = config.withVersionAliases(aliases);
                    tomlWriter.write(configPath, updated);
                    printVersionAliasSummary(normalizedAlias, normalizedVersion, previous);
                    if (noResolve) {
                        spec.commandLine().getOut().println("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                        return;
                    }
                    printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
                } catch (ArtifactCacheException
                        | ResolveException
                        | VersionAliasCommandException
                        | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }

            private void printVersionAliasSummary(String alias, String version, String previous) {
                if (version.equals(previous)) {
                    spec.commandLine().getOut().println(
                            "Version alias " + alias + " already equals " + version + " in [versions]");
                } else if (previous == null) {
                    spec.commandLine().getOut().println(
                            "Added version alias " + alias + " = " + version + " to [versions]");
                } else {
                    spec.commandLine().getOut().println(
                            "Updated version alias " + alias + " from " + previous + " to " + version + " in [versions]");
                }
            }
        }

        @Command(name = "remove", description = "Remove an unused version alias from zolt.toml and refresh zolt.lock.")
        public static final class RemoveCommand implements Runnable {
            @Parameters(index = "0", paramLabel = "ALIAS", description = "Version alias name.")
            private String alias;

            @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
            private boolean noResolve;

            @Option(names = "--cwd", hidden = true)
            private Path workingDirectory = Path.of(".");

            @Option(names = "--cache-root", hidden = true)
            private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

            @Spec
            private CommandSpec spec;

            private final ZoltTomlParser tomlParser = new ZoltTomlParser();
            private final ZoltTomlWriter tomlWriter = new ZoltTomlWriter();
            private final ResolveService resolveService = new ResolveService();

            @Override
            public void run() {
                try {
                    String normalizedAlias = validateVersionAlias(alias);
                    Path configPath = workingDirectory.resolve("zolt.toml");
                    ProjectConfig config = tomlParser.parse(configPath);
                    Map<String, String> aliases = new LinkedHashMap<>(config.versionAliases());
                    if (!aliases.containsKey(normalizedAlias)) {
                        throw new VersionAliasCommandException(
                                "Version alias `" + normalizedAlias + "` is not declared in [versions].");
                    }
                    List<String> references = versionAliasReferences(config, normalizedAlias);
                    if (!references.isEmpty()) {
                        throw new VersionAliasCommandException(
                                "Version alias `"
                                        + normalizedAlias
                                        + "` is still referenced by "
                                        + String.join(", ", references)
                                        + ". Remove or update those versionRef declarations before removing [versions]."
                                        + normalizedAlias
                                        + ".");
                    }
                    aliases.remove(normalizedAlias);
                    ProjectConfig updated = config.withVersionAliases(aliases);
                    tomlWriter.write(configPath, updated);
                    spec.commandLine().getOut().println(
                            "Removed version alias " + normalizedAlias + " from [versions]");
                    if (noResolve) {
                        spec.commandLine().getOut().println("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                        return;
                    }
                    printResolveResult(spec, resolveService.resolve(workingDirectory, updated, cacheRoot));
                } catch (ArtifactCacheException
                        | ResolveException
                        | VersionAliasCommandException
                        | ZoltConfigException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }
        }
    }

    @Command(name = "update", description = "Update the Zolt executable in place.")
    public static final class UpdateCommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().getOut().println("""
                    zolt update is not available yet.
                    Future behavior: check the release channel, download a verified native archive, replace the current zolt executable atomically, and keep a rollback copy.
                    Track this work in followUps/-design-zolt-update-command.md.
                    """.stripTrailing());
            return 1;
        }
    }

    @Command(name = "check", description = "Run Zolt-owned quality checks.")
    public static final class CheckCommand implements Callable<Integer> {
        enum Format {
            TEXT,
            JSON
        }

        @Option(names = "--check", description = "Run a quality check id. May be repeated.")
        private List<String> checks = List.of();

        @Option(names = "--context", description = "Apply a built-in check context. Supported values: local, ci.")
        private QualityCheckContext context;

        @Option(names = "--reports-dir", description = "Validate project-relative JUnit XML report output for CI context.")
        private Path reportsDir;

        @Option(names = "--require-package", description = "Require the configured package artifact and package evidence during CI context checks.")
        private boolean requirePackage;

        @Option(names = "--require-publish-dry-run", description = "Require publish dry-run preflight during CI context checks without uploading.")
        private boolean requirePublishDryRun;

        @Option(names = "--require-offline-ready", description = "Require locked dependency metadata to be available from the local cache during CI context checks.")
        private boolean requireOfflineReady;

        @Option(names = "--workspace", description = "Check workspace members using the workspace selection model.")
        private boolean workspace;

        @Option(names = "--offline", description = "Use only artifacts already present in the local cache for checks that need dependency metadata.")
        private boolean offline;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            TimingRecorder timings = timingRecorder(timingOptions);
            QualityCheckReport report = timings.measure(
                    "run quality checks",
                    () -> new QualityCheckService().check(new QualityCheckRequest(
                            workingDirectory,
                            cacheRoot,
                            offline,
                            workspace,
                            checks,
                            context,
                            reportsDir,
                            requirePackage,
                            requirePublishDryRun,
                            requireOfflineReady,
                            workspaceSelection(all, members, memberGroups))),
                    ZoltCli::qualityCheckAttributes);
            if (format == Format.JSON) {
                printAndFlush(spec, QualityCheckFormatter.json(report));
            } else {
                printAndFlush(spec, QualityCheckFormatter.text(report));
            }
            printTimings(spec, "check", workingDirectory, timingOptions, timings);
            return report.ok() ? 0 : 1;
        }
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

        @Option(names = "--version-ref", description = "Use a version alias declared in [versions].")
        private String versionRef;

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
            if (versionRef != null && versionRef.isBlank()) {
                throw new AddCommandException(
                        "Version alias for --version-ref must be non-empty. Use `--version-ref <alias>`.");
            }
            if (managed && versionRef != null) {
                throw new AddCommandException(
                        "`--managed` and `--version-ref` cannot be used together. Choose a platform-managed dependency or a named [versions] alias.");
            }
            if (versionRef != null && coordinate.version().isPresent()) {
                throw new AddCommandException(
                        "Version-ref dependency coordinate must not include a version. Use `--version-ref "
                                + versionRef
                                + " group:artifact`.");
            }
            if (managed) {
                return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), "", true, null);
            }
            if (versionRef != null) {
                return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), "", false, versionRef);
            }
            String version = coordinate.version().orElseThrow(() -> new AddCommandException(
                    "Dependency coordinate must include a version. Use `group:artifact:version` or add `--managed` when a declared platform should provide the version."));
            return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), version, false, null);
        }

        private ProjectConfig updateConfig(ProjectConfig config, AddRequest request) {
            if (request.managed()) {
                return tomlWriter.addManagedDependency(config, request.section(), request.coordinate());
            }
            if (request.versionRef() != null) {
                String version = config.versionAliases().get(request.versionRef());
                if (version == null) {
                    throw new AddCommandException(
                            "Unknown versionRef `"
                                    + request.versionRef()
                                    + "`. Add [versions]."
                                    + request.versionRef()
                                    + " or use an explicit version.");
                }
                return tomlWriter.addVersionRefDependency(
                        config,
                        request.section(),
                        request.coordinate(),
                        request.versionRef(),
                        version);
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
            String existingVersionRef = versionRef(original, request.section(), request.coordinate());
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
            if (request.versionRef() != null) {
                String version = original.versionAliases().get(request.versionRef());
                String versionRefDescription = "versionRef `" + request.versionRef() + "` = " + version;
                if (request.versionRef().equals(existingVersionRef)) {
                    spec.commandLine().getOut().println("Dependency " + request.coordinate()
                            + " already uses " + versionRefDescription + " in [" + section + "]");
                } else if (existingVersionRef != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from versionRef `" + existingVersionRef + "` to " + versionRefDescription
                            + " in [" + section + "]");
                } else if (existingManaged) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from managed version to " + versionRefDescription + " in [" + section + "]");
                } else if (existing != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from " + existing + " to " + versionRefDescription + " in [" + section + "]");
                } else if (existingWorkspace != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from workspace member " + existingWorkspace
                            + " to " + versionRefDescription + " in [" + section + "]");
                } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
                    spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                            + " from " + existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                            + " to " + versionRefDescription + " in [" + section + "]");
                } else {
                    spec.commandLine().getOut().println("Added dependency " + request.coordinate()
                            + " with " + versionRefDescription + " to [" + section + "]");
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
            @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT[:VERSION]", description = "Platform BOM coordinate.")
            private String coordinate;

            @Option(names = "--version-ref", description = "Use a version alias declared in [versions].")
            private String versionRef;

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
                    String platform = parsed.groupId() + ":" + parsed.artifactId();
                    Path configPath = workingDirectory.resolve("zolt.toml");
                    ProjectConfig config = tomlParser.parse(configPath);
                    PlatformAddRequest request = addRequest(config, parsed, platform);
                    ProjectConfig updated = request.versionRef() == null
                            ? tomlWriter.addPlatform(config, platform, request.version())
                            : tomlWriter.addVersionRefPlatform(
                                    config,
                                    platform,
                                    request.versionRef(),
                                    request.version());
                    tomlWriter.write(configPath, updated);
                    printAddSummary(config, platform, request);
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

            private PlatformAddRequest addRequest(ProjectConfig config, Coordinate parsed, String platform) {
                if (versionRef != null && versionRef.isBlank()) {
                    throw new PlatformCommandException(
                            "Version alias for --version-ref must be non-empty. Use `--version-ref <alias>`.");
                }
                if (versionRef != null && parsed.version().isPresent()) {
                    throw new PlatformCommandException(
                            "Version-ref platform coordinate must not include a version. Use `--version-ref "
                                    + versionRef
                                    + " "
                                    + platform
                                    + "`.");
                }
                if (versionRef == null) {
                    String version = parsed.version().orElseThrow(() -> new PlatformCommandException(
                            "Platform coordinate must include a version. Use `group:artifact:version` or `--version-ref <alias> group:artifact`."));
                    return new PlatformAddRequest(version, null);
                }
                String version = config.versionAliases().get(versionRef);
                if (version == null) {
                    throw new PlatformCommandException(
                            "Unknown versionRef `"
                                    + versionRef
                                    + "`. Add [versions]."
                                    + versionRef
                                    + " or use an explicit version.");
                }
                return new PlatformAddRequest(version, versionRef);
            }

            private void printAddSummary(ProjectConfig original, String platform, PlatformAddRequest request) {
                String version = request.version();
                String existing = original.platforms().get(platform);
                String existingVersionRef = platformVersionRef(original, platform);
                if (request.versionRef() != null) {
                    String versionRefDescription = "versionRef `" + request.versionRef() + "` = " + version;
                    if (request.versionRef().equals(existingVersionRef)) {
                        spec.commandLine().getOut().println("Platform " + platform + " already uses "
                                + versionRefDescription + " in [platforms]");
                    } else if (existingVersionRef != null) {
                        spec.commandLine().getOut().println("Updated platform " + platform
                                + " from versionRef `" + existingVersionRef + "` to "
                                + versionRefDescription + " in [platforms]");
                    } else if (existing != null) {
                        spec.commandLine().getOut().println("Updated platform " + platform
                                + " from " + existing + " to " + versionRefDescription + " in [platforms]");
                    } else {
                        spec.commandLine().getOut().println("Added platform " + platform
                                + " with " + versionRefDescription + " to [platforms]");
                    }
                    return;
                }
                if (existingVersionRef != null) {
                    spec.commandLine().getOut().println("Updated platform " + platform
                            + " from versionRef `" + existingVersionRef + "` to " + version + " in [platforms]");
                } else if (version.equals(existing)) {
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

            private static String platformVersionRef(ProjectConfig config, String platform) {
                DependencyMetadata metadata =
                        config.dependencyMetadata().get(DependencyMetadata.key("platforms", platform));
                return metadata == null ? null : metadata.versionRef();
            }

            private record PlatformAddRequest(String version, String versionRef) {}
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

        @Option(
                names = "--repository-overlay",
                description = "Opt into a user-local repository overlay. Supported values: maven-local, local-maven.")
        private List<String> repositoryOverlays = List.of();

        @Option(
                names = "--no-local-overlays",
                description = "Reject lockfile packages resolved from local repository overlays.")
        private boolean noLocalOverlays;

        @Option(names = "--workspace", description = "Resolve the discovered workspace and write the root zolt.lock.")
        private boolean workspace;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Option(names = "--maven-local-root", hidden = true)
        private Path mavenLocalRoot = Path.of(System.getProperty("user.home"), ".m2", "repository");

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                if (workspace) {
                    if (!repositoryOverlays.isEmpty() || noLocalOverlays) {
                        throw new ResolveException(
                                "Repository overlay options are currently supported for single-project resolve only. "
                                        + "Run without --workspace or wait for workspace overlay policy support.");
                    }
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
                                resolveOptions()),
                        ZoltCli::resolveAttributes);
                printResolveResult(spec, result, !locked);
            } catch (ArtifactCacheException | ResolveException | WorkspaceConfigException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "resolve", workingDirectory, timingOptions, timings);
            }
        }

        private ResolveOptions resolveOptions() {
            List<RepositoryOverlay> overlays = new ArrayList<>();
            for (String overlay : repositoryOverlays) {
                overlays.add(repositoryOverlay(overlay));
            }
            return new ResolveOptions(offline, overlays, noLocalOverlays);
        }

        private RepositoryOverlay repositoryOverlay(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "maven-local", "local-maven" -> RepositoryOverlay.mavenLocal(mavenLocalRoot);
                default -> throw new ResolveException(
                        "Unsupported repository overlay `"
                                + value
                                + "`. Supported overlays: maven-local.");
            };
        }
    }

    @Command(name = "tree", description = "Display the resolved dependency graph.")
    public static final class TreeCommand implements Runnable {
        enum Format {
            TEXT,
            JSON
        }

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                ZoltLockfile lockfile = new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock"));
                String output = format == Format.JSON
                        ? new DependencyJsonFormatter().tree(config, lockfile)
                        : new DependencyTreeFormatter().format(config, lockfile);
                printAndFlush(spec, output);
            } catch (LockfileReadException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "why", description = "Explain why a package is present.")
    public static final class WhyCommand implements Runnable {
        enum Format {
            TEXT,
            JSON
        }

        @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT", description = "Package id to explain.")
        private String packageId;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Spec
        private CommandSpec spec;

        private final CoordinateParser coordinateParser = new CoordinateParser();

        @Override
        public void run() {
            try {
                Coordinate coordinate = coordinateParser.parse(packageId);
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                ZoltLockfile lockfile = new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock"));
                PackageId target = new PackageId(coordinate.groupId(), coordinate.artifactId());
                String output = format == Format.JSON
                        ? new DependencyJsonFormatter().why(config, lockfile, target)
                        : new DependencyWhyFormatter().format(config, lockfile, target);
                printAndFlush(spec, output);
            } catch (CoordinateParseException | DependencyWhyException | LockfileReadException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(name = "policy", description = "Show dependency baseline and policy diagnostics.")
    public static final class PolicyCommand implements Runnable {
        enum Format {
            TEXT,
            JSON
        }

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                ZoltLockfile lockfile = new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock"));
                DependencyPolicyReport report = new DependencyPolicyReportService().report(
                        workingDirectory,
                        config,
                        lockfile);
                DependencyPolicyReportFormatter formatter = new DependencyPolicyReportFormatter();
                printAndFlush(spec, format == Format.JSON ? formatter.json(report) : formatter.text(report));
            } catch (DependencyPolicyReportException | LockfileReadException | ZoltConfigException exception) {
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

    @Command(name = "plan", description = "Show the typed Zolt command plan without executing it.")
    public static final class PlanCommand implements Callable<Integer> {
        enum Format {
            TEXT,
            JSON
        }

        @Option(names = "--target", description = "Plan target: build, test, package, or ci.")
        private PlanTarget target = PlanTarget.PACKAGE;

        @Option(names = "--reports-dir", description = "Include a project-relative test report output in test/ci plans.")
        private Path reportsDir;

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            try {
                ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
                TestReportSettings reportSettings = TestReportSettings.reportsDirectory(reportsDir);
                BuildPlan plan = new BuildPlanService().plan(
                        workingDirectory,
                        config,
                        target,
                        reportSettings.reportsDirectory());
                BuildPlanFormatter formatter = new BuildPlanFormatter();
                if (format == Format.JSON) {
                    printAndFlush(spec, formatter.json(plan));
                } else {
                    printAndFlush(spec, formatter.text(plan));
                }
                return plan.blocked() ? 1 : 0;
            } catch (TestRunException | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            }
        }
    }

    @Command(
            name = "explain",
            mixinStandardHelpOptions = true,
            description = "Audit a Maven or Gradle project for future Zolt migration.")
    public static final class ExplainCommand implements Callable<Integer> {
        enum Format {
            TEXT,
            JSON
        }

        enum Source {
            AUTO,
            MAVEN,
            GRADLE
        }

        @Option(names = "--format", description = "Output format: text or json.")
        private Format format = Format.TEXT;

        @Option(names = "--source", description = "Project source type: auto, maven, or gradle.")
        private Source source = Source.AUTO;

        @Option(names = "--scorecard", description = "Print a migration readiness scorecard instead of the raw explain report.")
        private boolean scorecard;

        @Option(names = "--blockers", description = "Print a focused migration blocker report instead of the raw explain report.")
        private boolean blockers;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            Path root = workingDirectory.toAbsolutePath().normalize();
            if (scorecard && blockers) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(),
                        "`--scorecard` and `--blockers` select different explain reports. Choose one.");
            }
            Source detectedSource = detectSource(root);
            if (detectedSource == Source.MAVEN) {
                try {
                    MavenInspectionResult result = new MavenStaticProjectInspector().inspect(root);
                    if (blockers) {
                        MigrationBlockerReportFormatter formatter = new MigrationBlockerReportFormatter();
                        if (format == Format.JSON) {
                            printAndFlush(spec, formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                        } else {
                            printAndFlush(spec, formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                        }
                        return 0;
                    }
                    if (scorecard) {
                        MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
                        if (format == Format.JSON) {
                            printAndFlush(spec, formatter.json(MigrationReadinessScorecards.from(result)));
                        } else {
                            printAndFlush(spec, formatter.text(MigrationReadinessScorecards.from(result)));
                        }
                        return 0;
                    }
                    MavenExplainFormatter formatter = new MavenExplainFormatter();
                    if (format == Format.JSON) {
                        printAndFlush(spec, formatter.json(result));
                    } else {
                        printAndFlush(spec, formatter.text(result));
                    }
                    return 0;
                } catch (MigrationExplainException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }
            if (detectedSource == Source.GRADLE) {
                try {
                    GradleInspectionResult result = new GradleStaticProjectInspector().inspect(root);
                    if (blockers) {
                        MigrationBlockerReportFormatter formatter = new MigrationBlockerReportFormatter();
                        if (format == Format.JSON) {
                            printAndFlush(spec, formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                        } else {
                            printAndFlush(spec, formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                        }
                        return 0;
                    }
                    if (scorecard) {
                        MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
                        if (format == Format.JSON) {
                            printAndFlush(spec, formatter.json(MigrationReadinessScorecards.from(result)));
                        } else {
                            printAndFlush(spec, formatter.text(MigrationReadinessScorecards.from(result)));
                        }
                        return 0;
                    }
                    GradleExplainFormatter formatter = new GradleExplainFormatter();
                    if (format == Format.JSON) {
                        printAndFlush(spec, formatter.json(result));
                    } else {
                        printAndFlush(spec, formatter.text(result));
                    }
                    return 0;
                } catch (MigrationExplainException exception) {
                    spec.commandLine().getErr().println("error: " + exception.getMessage());
                    throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
                }
            }
            if (format == Format.JSON) {
                spec.commandLine().getOut().println("""
                        {"schemaVersion":1,"command":"explain","status":"not-implemented","source":"%s","root":"%s","message":"zolt explain is a future migration-audit command. It will inspect Maven and Gradle metadata statically without executing Maven or Gradle.","nextStep":"Track implementation in followUps/-add-zolt-explain-command-scaffold.md through followUps/-add-migration-explain-fixtures-and-golden-tests.md."}
                        """.formatted(detectedSource.name().toLowerCase(), jsonEscape(root.toString())).stripTrailing());
                return 1;
            }
            spec.commandLine().getOut().println("""
                    zolt explain is not implemented yet.

                    Planned behavior:
                      - audit Maven and Gradle project metadata statically
                      - report what Zolt can build, test, package, and cache
                      - report non-determinism and migration blockers
                      - emit deterministic text or JSON reports

                    This command will not execute Maven or Gradle and will not create compatibility mode.

                    Requested source: %s
                    Project root: %s
                    Track this work in followUps/-add-zolt-explain-command-scaffold.md through followUps/-add-migration-explain-fixtures-and-golden-tests.md.
                    """.formatted(detectedSource.name().toLowerCase(), root).stripTrailing());
            return 1;
        }

        private Source detectSource(Path root) {
            if (source != Source.AUTO) {
                return source;
            }
            if (java.nio.file.Files.isRegularFile(root.resolve("pom.xml"))) {
                return Source.MAVEN;
            }
            if (java.nio.file.Files.isRegularFile(root.resolve("settings.gradle"))
                    || java.nio.file.Files.isRegularFile(root.resolve("settings.gradle.kts"))
                    || java.nio.file.Files.isRegularFile(root.resolve("build.gradle"))
                    || java.nio.file.Files.isRegularFile(root.resolve("build.gradle.kts"))) {
                return Source.GRADLE;
            }
            return Source.AUTO;
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
            QUARKUS_DEPLOYMENT("quarkus-deployment"),
            AUDIT("audit");

            private static final String SUPPORTED =
                    "compile, runtime, test, processor, test-processor, quarkus-deployment, or audit";

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
                                + "`. Use " + SUPPORTED + ".");
            }
        }

        enum Format {
            TEXT,
            JSON
        }

        @Parameters(
                index = "0",
                paramLabel = "compile|runtime|test|processor|test-processor|quarkus-deployment|audit",
                description = "Classpath kind to print, or audit to inspect all Zolt-owned lanes.")
        private String kind;

        @Option(names = "--format", description = "Output format for audit: text or json.")
        private Format format = Format.TEXT;

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
                Path configPath = workingDirectory.resolve("zolt.toml");
                if (java.nio.file.Files.isRegularFile(configPath)) {
                    ProjectConfig config = new ZoltTomlParser().parse(configPath);
                    requireFreshLockfile(workingDirectory, config, cacheRoot, false);
                }
                ZoltLockfile lockfile = lockfileReader.read(workingDirectory.resolve("zolt.lock"));
                Kind parsedKind = Kind.parse(kind);
                if (parsedKind == Kind.AUDIT) {
                    ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();
                    String output = format == Format.JSON
                            ? formatter.formatJson(lockfile)
                            : formatter.formatText(lockfile);
                    printAndFlush(spec, output);
                    return;
                }
                if (format == Format.JSON) {
                    throw new ClasspathCommandException(
                            "`zolt classpath --format json` is supported for `audit` only. "
                                    + "Use `zolt classpath audit --format json`.");
                }
                ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
                String output = new ClasspathFormatter().format(switch (parsedKind) {
                    case COMPILE -> classpaths.compile();
                    case RUNTIME -> classpaths.runtime();
                    case TEST -> classpaths.test();
                    case PROCESSOR -> classpaths.processor();
                    case TEST_PROCESSOR -> classpaths.testProcessor();
                    case QUARKUS_DEPLOYMENT -> classpaths.quarkusDeployment();
                    case AUDIT -> throw new ClasspathCommandException("Classpath audit should be handled before formatting.");
                });
                printAndFlush(spec, output);
            } catch (ArtifactCacheException
                    | ClasspathCommandException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
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
                                        true,
                                        offline,
                                        timings),
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
                                    true,
                                    offline,
                                    timings),
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
                    requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, offline);
                    WorkspaceBuildService workspaceBuildService = new WorkspaceBuildService();
                    WorkspaceBuildResult result = timings.measure(
                            "build workspace",
                            () -> {
                                WorkspaceBuildPlan plan = timings.measure(
                                        "plan workspace build",
                                        () -> workspaceBuildService.planBuild(
                                                workingDirectory,
                                                cacheRoot,
                                                offline,
                                                workspaceSelection(all, members, memberGroups)),
                                        ZoltCli::workspaceBuildPlanAttributes);
                                return timings.measure(
                                        "compile workspace members",
                                        () -> workspaceBuildService.build(plan, cacheRoot),
                                        ZoltCli::workspaceBuildAttributes);
                            },
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
                requireFreshLockfile(workingDirectory, config, cacheRoot, offline);
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
                    | GroovyCompileException
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

        @Mixin
        private TimingOptions timingOptions = new TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = timingRecorder(timingOptions);
            try {
                if (workspace) {
                    requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
                    WorkspaceRunService workspaceRunService = new WorkspaceRunService();
                    WorkspaceRunResult result = timings.measure(
                            "run workspace",
                            () -> {
                                WorkspaceBuildPlan plan = timings.measure(
                                        "plan workspace run",
                                        () -> workspaceRunService.planRun(
                                                workingDirectory,
                                                cacheRoot,
                                                workspaceSelection(all, members, memberGroups)),
                                        ZoltCli::workspaceBuildPlanAttributes);
                                WorkspaceBuildResult buildResult = timings.measure(
                                        "build workspace run inputs",
                                        () -> workspaceRunService.buildRunInputs(plan, cacheRoot),
                                        ZoltCli::workspaceBuildAttributes);
                                return timings.measure(
                                        "launch workspace members",
                                        () -> workspaceRunService.runBuiltMembers(
                                                plan,
                                                buildResult,
                                                arguments,
                                                output -> {
                                                    spec.commandLine().getOut().print(output);
                                                    spec.commandLine().getOut().flush();
                                                }),
                                        ZoltCli::workspaceRunAttributes);
                            },
                            ZoltCli::workspaceRunAttributes);
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
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                requireFreshLockfile(workingDirectory, config, cacheRoot, false);
                RunResult result = timings.measure(
                        "run application",
                        () -> new RunService().run(
                                workingDirectory,
                                config,
                                cacheRoot,
                                arguments,
                                output -> {
                                    spec.commandLine().getOut().print(output);
                                    spec.commandLine().getOut().flush();
                                }),
                        ZoltCli::runAttributes);
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
                    | GroovyCompileException
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
            } finally {
                printTimings(spec, "run", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(
            name = "test",
            mixinStandardHelpOptions = true,
            description = "Compile and run tests, starting with JUnit support.")
    public static final class TestCommand implements Runnable {
        @Option(names = "--workspace", description = "Test workspace members in dependency order.")
        private boolean workspace;

        @Option(names = "--all", description = "Select every workspace member.")
        private boolean all;

        @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
        private List<String> members = List.of();

        @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
        private List<String> memberGroups = List.of();

        @Option(names = "--test", description = "Select one test class or method. May be repeated.")
        private List<String> testSelectors = List.of();

        @Option(names = "--tests", description = "Select test classes by glob-style class-name pattern. May be repeated.")
        private List<String> testPatterns = List.of();

        @Option(names = "--include-tag", description = "Include tests with a JUnit Platform tag. May be repeated.")
        private List<String> includedTags = List.of();

        @Option(names = "--exclude-tag", description = "Exclude tests with a JUnit Platform tag. May be repeated.")
        private List<String> excludedTags = List.of();

        @Option(names = "--jvm-arg", description = "Pass one JVM argument to the test runner process. May be repeated.")
        private List<String> jvmArgs = List.of();

        @Option(names = "--test-event", description = "Show JUnit test events: passed, skipped, or failed. May be repeated.")
        private List<String> testEvents = List.of();

        @Option(names = "--reports-dir", description = "Write JUnit XML reports to a project-relative directory.")
        private Path reportsDir;

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
                TestSelection testSelection = TestSelection.fromCli(
                        testSelectors,
                        testPatterns,
                        includedTags,
                        excludedTags);
                TestJvmArguments testJvmArguments = TestJvmArguments.fromCli(jvmArgs);
                List<String> requestedTestEvents = validatedTestEvents(testEvents);
                TestReportSettings reportSettings = TestReportSettings.reportsDirectory(reportsDir);
                if (workspace) {
                    requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
                    WorkspaceTestService workspaceTestService = new WorkspaceTestService();
                    WorkspaceTestResult result = timings.measure(
                            "test workspace",
                            () -> {
                                WorkspaceBuildPlan plan = timings.measure(
                                        "plan workspace tests",
                                        () -> workspaceTestService.planTests(
                                                workingDirectory,
                                                cacheRoot,
                                                workspaceSelection(all, members, memberGroups)),
                                        ZoltCli::workspaceBuildPlanAttributes);
                                WorkspaceBuildResult buildResult = timings.measure(
                                        "build workspace test inputs",
                                        () -> workspaceTestService.buildTestInputs(plan, cacheRoot),
                                        ZoltCli::workspaceBuildAttributes);
                                return timings.measure(
                                        "run workspace test members",
                                        () -> workspaceTestService.runTests(
                                                plan,
                                                buildResult,
                                                cacheRoot,
                                                testSelection,
                                                testJvmArguments,
                                                reportSettings,
                                                requestedTestEvents),
                                        ZoltCli::workspaceTestAttributes);
                            },
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
                        member.result().reportsDirectory().ifPresent(directory ->
                                spec.commandLine().getOut().println("Wrote test reports for "
                                        + member.member()
                                        + " to "
                                        + directory));
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
                requireFreshLockfile(workingDirectory, config, cacheRoot, false);
                TestRunService testRunService = new TestRunService();
                TestRunResult result = timings.measure(
                        "run tests",
                        () -> {
                            TestCompileResultWithClasspaths compileResult = timings.measure(
                                    "compile tests",
                                    () -> {
                                        BuildResultWithClasspaths buildResult = timings.measure(
                                                "build test inputs",
                                                () -> testRunService.buildTestInputs(
                                                        workingDirectory,
                                                        config,
                                                        cacheRoot),
                                                resultWithClasspaths -> buildAttributes(
                                                        resultWithClasspaths.buildResult()));
                                        TestCompileResult testCompileResult = timings.measure(
                                                "compile test sources",
                                                () -> testRunService.compileTests(
                                                        workingDirectory,
                                                        config,
                                                        buildResult.classpaths(),
                                                        buildResult.buildResult()),
                                                ZoltCli::testCompileAttributes);
                                        return new TestCompileResultWithClasspaths(
                                                testCompileResult,
                                                buildResult.classpaths());
                                    },
                                    resultWithClasspaths -> testCompileAttributes(
                                            resultWithClasspaths.testCompileResult()));
                            return timings.measure(
                                    "execute tests",
                                    () -> testRunService.runCompiledTests(
                                            workingDirectory,
                                            config,
                                            compileResult.classpaths(),
                                            compileResult.testCompileResult(),
                                            testSelection,
                                            testJvmArguments,
                                            reportSettings,
                                            requestedTestEvents),
                                    ZoltCli::testExecutionAttributes);
                        },
                        ZoltCli::testRunAttributes);
                printAndFlush(spec, result.output());
                if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().println("Tests passed");
                result.reportsDirectory().ifPresent(directory ->
                        spec.commandLine().getOut().println("Wrote test reports to " + directory));
            } catch (BuildException
                    | JavacException
                    | GroovyCompileException
                    | JavaRunException
                    | ResourceCopyException
                    | TestRunException
                    | TestSelectionException
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

    @Command(
            name = "coverage",
            mixinStandardHelpOptions = true,
            description = "Run tests with Jacoco instrumentation and write coverage reports.")
    public static final class CoverageCommand implements Runnable {
        @Option(names = "--test", description = "Select one test class or method. May be repeated.")
        private List<String> testSelectors = List.of();

        @Option(names = "--tests", description = "Select test classes by glob-style class-name pattern. May be repeated.")
        private List<String> testPatterns = List.of();

        @Option(names = "--include-tag", description = "Include tests with a JUnit Platform tag. May be repeated.")
        private List<String> includedTags = List.of();

        @Option(names = "--exclude-tag", description = "Exclude tests with a JUnit Platform tag. May be repeated.")
        private List<String> excludedTags = List.of();

        @Option(names = "--test-event", description = "Show JUnit test events: passed, skipped, or failed. May be repeated.")
        private List<String> testEvents = List.of();

        @Option(names = "--no-xml", description = "Disable the Jacoco XML report.")
        private boolean noXml;

        @Option(names = "--no-html", description = "Disable the Jacoco HTML report.")
        private boolean noHtml;

        @Option(names = "--exec-file", description = "Project-relative Jacoco execution data path.")
        private Path execFile = Path.of("target/coverage/jacoco.exec");

        @Option(names = "--xml-report", description = "Project-relative Jacoco XML report path.")
        private Path xmlReport = Path.of("target/coverage/jacoco.xml");

        @Option(names = "--html-dir", description = "Project-relative Jacoco HTML report directory.")
        private Path htmlDirectory = Path.of("target/coverage/html");

        @Option(names = "--reports-dir", description = "Write JUnit XML reports to a project-relative directory.")
        private Path reportsDir = Path.of("target/coverage/test-reports");

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
                TestSelection testSelection = TestSelection.fromCli(
                        testSelectors,
                        testPatterns,
                        includedTags,
                        excludedTags);
                List<String> requestedTestEvents = validatedTestEvents(testEvents);
                CoverageReportSettings reportSettings = new CoverageReportSettings(
                        !noXml,
                        !noHtml,
                        execFile,
                        xmlReport,
                        htmlDirectory,
                        TestReportSettings.reportsDirectory(reportsDir));
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
                CoverageResult result = timings.measure(
                        "run coverage",
                        () -> new CoverageService().runCoverage(
                                workingDirectory,
                                config,
                                cacheRoot,
                                testSelection,
                                reportSettings,
                                requestedTestEvents),
                        coverageResult -> Map.of(
                                "execFile", coverageResult.execFile().toString(),
                                "xmlReport", coverageResult.xmlReport().map(Path::toString).orElse("disabled"),
                                "htmlDirectory", coverageResult.htmlDirectory().map(Path::toString).orElse("disabled")));
                printAndFlush(spec, result.testRunResult().output());
                if (!result.testRunResult().output().isEmpty() && !result.testRunResult().output().endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                if (!result.reportOutput().isBlank()) {
                    printAndFlush(spec, result.reportOutput());
                    if (!result.reportOutput().endsWith("\n")) {
                        spec.commandLine().getOut().println();
                    }
                }
                spec.commandLine().getOut().println("Coverage reports written");
                spec.commandLine().getOut().println("Execution data: " + result.execFile());
                result.xmlReport().ifPresent(path -> spec.commandLine().getOut().println("XML report: " + path));
                result.htmlDirectory().ifPresent(path -> spec.commandLine().getOut().println("HTML report: " + path));
                result.testRunResult().reportsDirectory()
                        .ifPresent(path -> spec.commandLine().getOut().println("Test reports: " + path));
            } catch (BuildException
                    | CoverageException
                    | JavacException
                    | GroovyCompileException
                    | JavaRunException
                    | ResourceCopyException
                    | TestRunException
                    | TestSelectionException
                    | SourceDiscoveryException
                    | LockfileReadException
                    | ResolveException
                    | ZoltConfigException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
            } finally {
                printTimings(spec, "coverage", workingDirectory, timingOptions, timings);
            }
        }
    }

    private static List<String> validatedTestEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<String> validated = new ArrayList<>();
        for (String event : events) {
            try {
                TestRuntimeSettings.validateEvent("--test-event", event);
            } catch (IllegalArgumentException exception) {
                throw new TestRunException(exception.getMessage(), exception);
            }
            if (!validated.contains(event)) {
                validated.add(event);
            }
        }
        return List.copyOf(validated);
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

        @Option(names = "--mode", description = "Package mode: thin, spring-boot, war, spring-boot-war, quarkus, or uber.")
        private String mode;

        @Option(names = "--plan", description = "Print the package content plan without building or writing the archive.")
        private boolean planOnly;

        @Option(names = "--format", description = "Package plan output format: text or json.")
        private String format = "text";

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
                PlanOutputFormat planOutputFormat = planOutputFormat(format);
                if (!planOnly && planOutputFormat != PlanOutputFormat.TEXT) {
                    throw new PackageException("Package --format is only supported with --plan. Use `zolt package --plan --format json`.");
                }
                if (workspace) {
                    if (planOnly) {
                        throw new PackageException("Package --plan is currently single-project. Run it from the member project you want to inspect.");
                    }
                    requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
                    WorkspacePackageService workspacePackageService = new WorkspacePackageService();
                    WorkspacePackageResult result = timings.measure(
                            "package workspace",
                            () -> {
                                WorkspaceBuildPlan plan = timings.measure(
                                        "plan workspace packages",
                                        () -> workspacePackageService.planPackages(
                                                workingDirectory,
                                                cacheRoot,
                                                workspaceSelection(all, members, memberGroups)),
                                        ZoltCli::workspaceBuildPlanAttributes);
                                WorkspaceBuildResult buildResult = timings.measure(
                                        "build workspace package inputs",
                                        () -> workspacePackageService.buildPackageInputs(plan, cacheRoot),
                                        ZoltCli::workspaceBuildAttributes);
                                return timings.measure(
                                        "assemble workspace packages",
                                        () -> workspacePackageService.packageBuiltJars(
                                                plan,
                                                buildResult,
                                                packageModeOverride),
                                        ZoltCli::workspacePackageAttributes);
                            },
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
                        spec.commandLine().getOut().println("Wrote archive to " + member.result().jarPath());
                        member.result().evidenceManifestPath().ifPresent(path ->
                                spec.commandLine().getOut().println("Wrote package evidence to " + path));
                        for (PackageArtifact artifact : member.result().artifacts()) {
                            spec.commandLine().getOut().println(
                                    "Wrote "
                                            + artifact.classifier()
                                            + " jar to "
                                            + artifact.path());
                        }
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
                requireFreshLockfile(workingDirectory, config, cacheRoot, false);
                if (planOnly) {
                    PackagePlan packagePlan = timings.measure(
                            "plan package contents",
                            () -> new PackagePlanService().plan(workingDirectory, config),
                            ZoltCli::packagePlanAttributes);
                    PackagePlanFormatter formatter = new PackagePlanFormatter();
                    if (planOutputFormat == PlanOutputFormat.JSON) {
                        printAndFlush(spec, formatter.json(packagePlan));
                    } else {
                        printAndFlush(spec, formatter.text(packagePlan));
                    }
                    return;
                }
                PackageService packageService = new PackageService();
                PackageResult result = timings.measure(
                        "package",
                        () -> {
                            packageService.preparePackageToolingIfNeeded(workingDirectory, config, cacheRoot);
                            BuildResultWithClasspaths buildResult = timings.measure(
                                    "build package inputs",
                                    () -> new BuildService().buildWithClasspaths(
                                            workingDirectory,
                                            config,
                                            cacheRoot,
                                            false),
                                    resultWithClasspaths -> buildAttributes(resultWithClasspaths.buildResult()));
                            return timings.measure(
                                    "assemble package",
                                    () -> packageService.packageJar(workingDirectory, config, buildResult, cacheRoot),
                                    ZoltCli::packageAttributes);
                        },
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
                    } else if (result.mode() == PackageMode.SPRING_BOOT_WAR) {
                        spec.commandLine().getOut().println("Run with Zolt: zolt run-package --mode spring-boot-war -- [args]");
                    } else if (result.mode() == PackageMode.QUARKUS) {
                        spec.commandLine().getOut().println("Run with Zolt: zolt run");
                    } else {
                        spec.commandLine().getOut().println("Run with dependencies: zolt run-package -- [args]");
                    }
                } else if (result.mode() == PackageMode.WAR) {
                    spec.commandLine().getOut().println("WAR is a servlet container deployment artifact; use `spring-boot-war` for java -jar.");
                } else {
                    spec.commandLine().getOut().println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
                }
                if (result.mode() == PackageMode.SPRING_BOOT) {
                    spec.commandLine().getOut().println("Spring Boot jar: dependencies are nested under BOOT-INF/lib.");
                } else if (result.mode() == PackageMode.WAR) {
                    spec.commandLine().getOut().println("WAR: application classes are under WEB-INF/classes and runtime dependencies are under WEB-INF/lib.");
                } else if (result.mode() == PackageMode.SPRING_BOOT_WAR) {
                    spec.commandLine().getOut().println("Spring Boot WAR: runtime dependencies are under WEB-INF/lib and provided dependencies are under WEB-INF/lib-provided.");
                } else if (result.mode() == PackageMode.QUARKUS) {
                    spec.commandLine().getOut().println("Quarkus fast-jar: deploy the whole target/quarkus-app directory.");
                } else {
                    spec.commandLine().getOut().println("Thin jar: dependencies are not bundled.");
                    result.runtimeClasspathPath().ifPresent(path ->
                            spec.commandLine().getOut().println("Wrote runtime classpath to " + path));
                }
                spec.commandLine().getOut().println("Wrote archive to " + result.jarPath());
                result.evidenceManifestPath().ifPresent(path ->
                        spec.commandLine().getOut().println("Wrote package evidence to " + path));
                for (PackageArtifact artifact : result.artifacts()) {
                    spec.commandLine().getOut().println(
                            "Wrote "
                                    + artifact.classifier()
                                    + " jar to "
                                    + artifact.path());
                }
            } catch (BuildException
                    | JavacException
                    | GroovyCompileException
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

    @Command(name = "publish", description = "Publish Zolt-produced artifacts to Maven-compatible repositories.")
    public static final class PublishCommand implements Callable<Integer> {
        @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
        private boolean dryRun;

        @Option(names = "--context", description = "Apply a publish context policy. Supported values: release.")
        private PublishContext context;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            try {
                if (context != null && !dryRun) {
                    spec.commandLine().getErr().println("error: Publish context policy is currently supported only with --dry-run.");
                    spec.commandLine().getErr().flush();
                    return 1;
                }
                if (dryRun) {
                    PublishDryRunPlan plan = new PublishDryRunService().plan(workingDirectory);
                    if (context == PublishContext.RELEASE) {
                        plan = new PublishReleasePolicyService().apply(workingDirectory, plan);
                    }
                    printAndFlush(spec, PublishDryRunFormatter.text(plan));
                    return plan.ok() ? 0 : 1;
                }
                PublishUploadResult result = new PublishUploadService().upload(workingDirectory);
                printAndFlush(spec, PublishUploadFormatter.text(result));
                return 0;
            } catch (PublishException | ZoltConfigException | PackageException exception) {
                spec.commandLine().getErr().println("error: " + exception.getMessage());
                spec.commandLine().getErr().flush();
                return 1;
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

        @Option(names = "--mode", description = "Package mode: thin, spring-boot, war, spring-boot-war, quarkus, or uber.")
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
                    requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
                    WorkspaceRunPackageService workspaceRunPackageService = new WorkspaceRunPackageService();
                    WorkspaceRunPackageResult result = timings.measure(
                            "run workspace packages",
                            () -> {
                                WorkspaceBuildPlan plan = timings.measure(
                                        "plan workspace run packages",
                                        () -> workspaceRunPackageService.planRunPackages(
                                                workingDirectory,
                                                cacheRoot,
                                                workspaceSelection(all, members, memberGroups)),
                                        ZoltCli::workspaceBuildPlanAttributes);
                                WorkspaceBuildResult buildResult = timings.measure(
                                        "build workspace run-package inputs",
                                        () -> workspaceRunPackageService.buildRunPackageInputs(plan, cacheRoot),
                                        ZoltCli::workspaceBuildAttributes);
                                WorkspacePackageResult packageResult = timings.measure(
                                        "assemble workspace run packages",
                                        () -> workspaceRunPackageService.packageRunPackageInputs(
                                                plan,
                                                buildResult,
                                                packageModeOverride),
                                        ZoltCli::workspacePackageAttributes);
                                return timings.measure(
                                        "launch workspace packages",
                                        () -> workspaceRunPackageService.runPackagedMembers(
                                                plan,
                                                packageResult,
                                                arguments),
                                        ZoltCli::workspaceRunPackageAttributes);
                            },
                            ZoltCli::workspaceRunPackageAttributes);
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
                        timings.measure(
                                "config read",
                                () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"))),
                        packageModeOverride);
                requireFreshLockfile(workingDirectory, config, cacheRoot, false);
                RunPackageResult result = timings.measure(
                        "run packaged application",
                        () -> new RunPackageService().runPackage(
                                workingDirectory,
                                config,
                                cacheRoot,
                                arguments),
                        ZoltCli::runPackageAttributes);
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
                    | GroovyCompileException
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
            } finally {
                printTimings(spec, "run-package", workingDirectory, timingOptions, timings);
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
                    | GroovyCompileException
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
        @Option(names = "--target", description = "Release target. Supported: macos-arm64, macos-x64, linux-arm64, linux-x64, windows-x64.")
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
                    | GroovyCompileException
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
        java.util.LinkedHashMap<String, String> attributes = new java.util.LinkedHashMap<>();
        attributes.put("resolvedPackages", Integer.toString(result.resolvedCount()));
        attributes.put("downloadedArtifacts", Integer.toString(result.downloadCount()));
        attributes.put("conflicts", Integer.toString(result.conflictCount()));
        attributes.put("pomCacheHits", Integer.toString(result.metrics().pomCacheHits()));
        attributes.put("pomCacheMisses", Integer.toString(result.metrics().pomCacheMisses()));
        attributes.put("jarCacheHits", Integer.toString(result.metrics().jarCacheHits()));
        attributes.put("jarCacheMisses", Integer.toString(result.metrics().jarCacheMisses()));
        attributes.put("artifactCacheHits", Integer.toString(result.metrics().artifactCacheHits()));
        attributes.put("artifactCacheMisses", Integer.toString(result.metrics().artifactCacheMisses()));
        attributes.put("rawPomCacheHits", Integer.toString(result.metrics().rawPomCacheHits()));
        attributes.put("rawPomCacheMisses", Integer.toString(result.metrics().rawPomCacheMisses()));
        attributes.put("effectivePomCacheHits", Integer.toString(result.metrics().effectivePomCacheHits()));
        attributes.put("effectivePomCacheMisses", Integer.toString(result.metrics().effectivePomCacheMisses()));
        attributes.put("pomCacheHitMillis", Long.toString(result.metrics().pomCacheHitNanos() / 1_000_000L));
        attributes.put("pomDownloadMillis", Long.toString(result.metrics().pomDownloadNanos() / 1_000_000L));
        attributes.put("jarCacheHitMillis", Long.toString(result.metrics().jarCacheHitNanos() / 1_000_000L));
        attributes.put("jarDownloadMillis", Long.toString(result.metrics().jarDownloadNanos() / 1_000_000L));
        attributes.put("artifactCacheHitMillis", Long.toString(result.metrics().artifactCacheHitNanos() / 1_000_000L));
        attributes.put("artifactDownloadMillis", Long.toString(result.metrics().artifactDownloadNanos() / 1_000_000L));
        attributes.put("rawPomParseMillis", Long.toString(result.metrics().rawPomParseNanos() / 1_000_000L));
        attributes.put("effectivePomBuildMillis", Long.toString(result.metrics().effectivePomBuildNanos() / 1_000_000L));
        attributes.put("graphTraversalMillis", Long.toString(result.metrics().graphTraversalNanos() / 1_000_000L));
        attributes.put("versionSelectionMillis", Long.toString(result.metrics().versionSelectionNanos() / 1_000_000L));
        attributes.put("lockfileAssemblyMillis", Long.toString(result.metrics().lockfileAssemblyNanos() / 1_000_000L));
        attributes.put("lockfileWriteMillis", Long.toString(result.metrics().lockfileWriteNanos() / 1_000_000L));
        attributes.put(
                "lockfileVerificationMillis", Long.toString(result.metrics().lockfileVerificationNanos() / 1_000_000L));
        attributes.put("pomCacheHitNanos", Long.toString(result.metrics().pomCacheHitNanos()));
        attributes.put("pomDownloadNanos", Long.toString(result.metrics().pomDownloadNanos()));
        attributes.put("jarCacheHitNanos", Long.toString(result.metrics().jarCacheHitNanos()));
        attributes.put("jarDownloadNanos", Long.toString(result.metrics().jarDownloadNanos()));
        attributes.put("artifactCacheHitNanos", Long.toString(result.metrics().artifactCacheHitNanos()));
        attributes.put("artifactDownloadNanos", Long.toString(result.metrics().artifactDownloadNanos()));
        attributes.put("rawPomParseNanos", Long.toString(result.metrics().rawPomParseNanos()));
        attributes.put("effectivePomBuildNanos", Long.toString(result.metrics().effectivePomBuildNanos()));
        attributes.put("graphTraversalNanos", Long.toString(result.metrics().graphTraversalNanos()));
        attributes.put("versionSelectionNanos", Long.toString(result.metrics().versionSelectionNanos()));
        attributes.put("lockfileAssemblyNanos", Long.toString(result.metrics().lockfileAssemblyNanos()));
        attributes.put("lockfileWriteNanos", Long.toString(result.metrics().lockfileWriteNanos()));
        attributes.put("lockfileVerificationNanos", Long.toString(result.metrics().lockfileVerificationNanos()));
        return attributes;
    }

    private static Map<String, String> buildAttributes(BuildResult result) {
        return Map.of(
                "sourceFiles", Integer.toString(result.sourceCount()),
                "resourceFiles", Integer.toString(result.resourceCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()),
                "mainCompilationSkipped", Boolean.toString(result.mainCompilationSkipped()));
    }

    private static void requireFreshLockfile(
            Path workingDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = workingDirectory.resolve("zolt.lock");
        if (!java.nio.file.Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        new ResolveService().resolve(workingDirectory, config, cacheRoot, true, offline);
    }

    private static void requireFreshWorkspaceLockfile(Path workingDirectory, Path cacheRoot, boolean offline) {
        Optional<Workspace> workspace = new WorkspaceDiscoveryService().discover(workingDirectory.toAbsolutePath().normalize());
        if (workspace.isEmpty()) {
            return;
        }
        Path lockfilePath = workspace.orElseThrow().root().resolve("zolt.lock");
        if (!java.nio.file.Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        new WorkspaceResolveService().resolve(workingDirectory, cacheRoot, true, offline);
    }

    private static boolean looksGeneratedLockfile(Path lockfilePath) {
        try {
            String content = java.nio.file.Files.readString(lockfilePath);
            return content.contains("Sha256 = ")
                    || content.contains("aliasFingerprint = ")
                    || content.contains("projectResolutionFingerprint = ");
        } catch (java.io.IOException exception) {
            throw new LockfileReadException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " while checking lockfile freshness. Check that the file exists and is readable.",
                    exception);
        }
    }

    private static Map<String, String> workspaceBuildAttributes(WorkspaceBuildResult result) {
        return Map.of(
                "members", Integer.toString(result.members().size()),
                "sourceFiles", Integer.toString(result.sourceCount()),
                "mainCompilationsSkipped", Integer.toString(result.mainCompilationSkippedCount()),
                "mainCompilationsExecuted", Integer.toString(result.mainCompilationExecutedCount()),
                "resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> workspaceBuildPlanAttributes(WorkspaceBuildPlan plan) {
        return Map.of(
                "includedMembers", Integer.toString(plan.selection().includedMembers().size()),
                "selectedMembers", Integer.toString(plan.selection().selectedMembers().size()),
                "resolvedLockfile", Boolean.toString(plan.resolvedLockfile()));
    }

    private static Map<String, String> testRunAttributes(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("mainSourceFiles", Integer.toString(result.compileResult().buildResult().sourceCount()));
        attributes.put("testSourceFiles", Integer.toString(result.compileResult().sourceCount()));
        attributes.put("testResourceFiles", Integer.toString(result.compileResult().resourceCount()));
        attributes.put("mainCompilationSkipped", Boolean.toString(result.compileResult().buildResult().mainCompilationSkipped()));
        attributes.put("testCompilationSkipped", Boolean.toString(result.compileResult().testCompilationSkipped()));
        attributes.put("testRunner", result.testRunner());
        attributes.put("testRuntimeClasspathEntries", Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put("testLauncherClasspathEntries", Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put("testDiscoveryScanRoots", Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put("testJvmArgs", Integer.toString(result.testJvmArguments().values().size()));
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put("outputBytes", Integer.toString(result.output().length()));
        return attributes;
    }

    private static Map<String, String> testCompileAttributes(TestCompileResult result) {
        return Map.of(
                "mainSourceFiles", Integer.toString(result.buildResult().sourceCount()),
                "testSourceFiles", Integer.toString(result.sourceCount()),
                "testResourceFiles", Integer.toString(result.resourceCount()),
                "mainCompilationSkipped", Boolean.toString(result.buildResult().mainCompilationSkipped()),
                "testCompilationSkipped", Boolean.toString(result.testCompilationSkipped()));
    }

    private static Map<String, String> testExecutionAttributes(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("testRunner", result.testRunner());
        attributes.put("testRuntimeClasspathEntries", Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put("testLauncherClasspathEntries", Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put("testDiscoveryScanRoots", Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put("testJvmArgs", Integer.toString(result.testJvmArguments().values().size()));
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put("outputBytes", Integer.toString(result.output().length()));
        return attributes;
    }

    private static void addPlainJunitWorkerTimingAttributes(Map<String, String> attributes, TestRunResult result) {
        if (result.testRunnerStartupNanos() >= 0L) {
            attributes.put("testRunnerStartupMillis", Long.toString(result.testRunnerStartupNanos() / 1_000_000L));
            attributes.put("testRunnerStartupNanos", Long.toString(result.testRunnerStartupNanos()));
        }
        if (result.testRunnerRequestNanos() >= 0L) {
            attributes.put("testRunnerRequestMillis", Long.toString(result.testRunnerRequestNanos() / 1_000_000L));
            attributes.put("testRunnerRequestNanos", Long.toString(result.testRunnerRequestNanos()));
        }
    }

    private static void addTestSelectionAttributes(Map<String, String> attributes, TestSelection selection) {
        attributes.put("testClassSelectors", Integer.toString(selection.classSelectors().size()));
        attributes.put("testMethodSelectors", Integer.toString(selection.methodSelectors().size()));
        attributes.put("testPatterns", Integer.toString(selection.classNamePatterns().size()));
        attributes.put("testIncludedTags", Integer.toString(selection.includedTags().size()));
        attributes.put("testExcludedTags", Integer.toString(selection.excludedTags().size()));
    }

    private static Map<String, String> workspaceTestAttributes(WorkspaceTestResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("members", Integer.toString(result.members().size()));
        attributes.put("mainSourceFiles", Integer.toString(result.mainSourceCount()));
        attributes.put("testSourceFiles", Integer.toString(result.testSourceCount()));
        attributes.put("mainCompilationsSkipped", Integer.toString(result.mainCompilationSkippedCount()));
        attributes.put("mainCompilationsExecuted", Integer.toString(result.mainCompilationExecutedCount()));
        attributes.put("testCompilationsSkipped", Integer.toString(result.testCompilationSkippedCount()));
        attributes.put("testCompilationsExecuted", Integer.toString(result.testCompilationExecutedCount()));
        attributes.put("testRuntimeClasspathEntries", Integer.toString(result.testRuntimeClasspathEntryCount()));
        attributes.put("testLauncherClasspathEntries", Integer.toString(result.testLauncherClasspathEntryCount()));
        attributes.put("testDiscoveryScanRoots", Integer.toString(result.testDiscoveryScanRootCount()));
        attributes.put("testClassSelectors", Integer.toString(result.testClassSelectorCount()));
        attributes.put("testMethodSelectors", Integer.toString(result.testMethodSelectorCount()));
        attributes.put("testPatterns", Integer.toString(result.testPatternCount()));
        attributes.put("testIncludedTags", Integer.toString(result.testIncludedTagCount()));
        attributes.put("testExcludedTags", Integer.toString(result.testExcludedTagCount()));
        attributes.put("resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
        return attributes;
    }

    private static Map<String, String> runAttributes(RunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("mainClass", result.javaRunResult().mainClass());
        attributes.put("mainSourceFiles", Integer.toString(result.buildResult().sourceCount()));
        attributes.put("resourceFiles", Integer.toString(result.buildResult().resourceCount()));
        attributes.put("mainCompilationSkipped", Boolean.toString(result.buildResult().mainCompilationSkipped()));
        attributes.put("resolvedLockfile", Boolean.toString(result.buildResult().resolvedLockfile()));
        attributes.put("outputBytes", Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    private static Map<String, String> workspaceRunAttributes(WorkspaceRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("members", Integer.toString(result.members().size()));
        attributes.put("mainSourceFiles", Integer.toString(workspaceRunSourceCount(result)));
        attributes.put("mainCompilationsSkipped", Integer.toString(workspaceRunMainCompilationSkippedCount(result)));
        attributes.put("mainCompilationsExecuted", Integer.toString(workspaceRunMainCompilationExecutedCount(result)));
        attributes.put("resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
        attributes.put("outputBytes", Integer.toString(workspaceRunOutputBytes(result)));
        return attributes;
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

    private static Map<String, String> runPackageAttributes(RunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("mode", result.packageResult().mode().configValue());
        attributes.put("entries", Integer.toString(result.packageResult().entryCount()));
        attributes.put("hasMainClass", Boolean.toString(result.packageResult().hasMainClass()));
        attributes.put("mainClass", result.javaRunResult().mainClass());
        attributes.put("resolvedLockfile", Boolean.toString(result.packageResult().buildResult().resolvedLockfile()));
        attributes.put("outputBytes", Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    private static Map<String, String> workspaceRunPackageAttributes(WorkspaceRunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("members", Integer.toString(result.members().size()));
        attributes.put("mainSourceFiles", Integer.toString(workspaceRunPackageSourceCount(result)));
        attributes.put("mainCompilationsSkipped", Integer.toString(workspaceRunPackageMainCompilationSkippedCount(result)));
        attributes.put("mainCompilationsExecuted", Integer.toString(workspaceRunPackageMainCompilationExecutedCount(result)));
        attributes.put("entries", Integer.toString(workspaceRunPackageEntryCount(result)));
        attributes.put("resolvedLockfile", Boolean.toString(result.resolvedLockfile()));
        attributes.put("outputBytes", Integer.toString(workspaceRunPackageOutputBytes(result)));
        return attributes;
    }

    private static int workspaceRunSourceCount(WorkspaceRunResult result) {
        return result.builtMembers().stream()
                .mapToInt(member -> member.result().sourceCount())
                .sum();
    }

    private static int workspaceRunMainCompilationSkippedCount(WorkspaceRunResult result) {
        return (int) result.builtMembers().stream()
                .filter(member -> member.result().mainCompilationSkipped())
                .count();
    }

    private static int workspaceRunMainCompilationExecutedCount(WorkspaceRunResult result) {
        return result.builtMembers().size() - workspaceRunMainCompilationSkippedCount(result);
    }

    private static int workspaceRunOutputBytes(WorkspaceRunResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().javaRunResult().output().length())
                .sum();
    }

    private static int workspaceRunPackageSourceCount(WorkspaceRunPackageResult result) {
        return result.builtMembers().stream()
                .mapToInt(member -> member.result().sourceCount())
                .sum();
    }

    private static int workspaceRunPackageMainCompilationSkippedCount(WorkspaceRunPackageResult result) {
        return (int) result.builtMembers().stream()
                .filter(member -> member.result().mainCompilationSkipped())
                .count();
    }

    private static int workspaceRunPackageMainCompilationExecutedCount(WorkspaceRunPackageResult result) {
        return result.builtMembers().size() - workspaceRunPackageMainCompilationSkippedCount(result);
    }

    private static int workspaceRunPackageEntryCount(WorkspaceRunPackageResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().packageResult().entryCount())
                .sum();
    }

    private static int workspaceRunPackageOutputBytes(WorkspaceRunPackageResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().javaRunResult().output().length())
                .sum();
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

    private static Map<String, String> qualityCheckAttributes(QualityCheckReport result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("checks", Integer.toString(result.checks().size()));
        attributes.put("passed", Long.toString(result.passedCount()));
        attributes.put("failed", Long.toString(result.failedCount()));
        attributes.put("skipped", Long.toString(result.skippedCount()));
        attributes.put("workspace", Boolean.toString(result.workspace()));
        attributes.put("ok", Boolean.toString(result.ok()));
        return attributes;
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

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
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
        String extension = (result.mode() == PackageMode.WAR || result.mode() == PackageMode.SPRING_BOOT_WAR)
                ? "war"
                : "jar";
        return "Packaged "
                + result.entryCount()
                + " compiled files as "
                + result.mode().configValue()
                + " "
                + extension;
    }

    private static Map<String, String> packagePlanAttributes(PackagePlan plan) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("mode", plan.mode().configValue());
        attributes.put("dependencies", String.valueOf(plan.dependencies().size()));
        attributes.put("warnings", String.valueOf(plan.warnings().size()));
        return attributes;
    }

    private enum PlanOutputFormat {
        TEXT,
        JSON
    }

    private static PlanOutputFormat planOutputFormat(String value) {
        String normalized = value == null || value.isBlank() ? "text" : value.trim().toLowerCase();
        return switch (normalized) {
            case "text" -> PlanOutputFormat.TEXT;
            case "json" -> PlanOutputFormat.JSON;
            default -> throw new PackageException("Unsupported package plan format `" + value + "`. Use text or json.");
        };
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

    private record AddRequest(
            DependencySection section,
            String coordinate,
            String version,
            boolean managed,
            String versionRef) {
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

    private static String versionRef(ProjectConfig config, DependencySection section, String coordinate) {
        DependencyMetadata metadata = config.dependencyMetadata().get(DependencyMetadata.key(sectionName(section), coordinate));
        return metadata == null ? null : metadata.versionRef();
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

    private static String validateVersionAlias(String alias) {
        if (alias == null || alias.isBlank() || !alias.equals(alias.trim())) {
            throw new VersionAliasCommandException(
                    "Version alias must be non-empty and must not contain leading or trailing whitespace.");
        }
        if (!VersionAliasRules.isValidName(alias)) {
            throw new VersionAliasCommandException(
                    "Invalid version alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
        return alias;
    }

    private static String validateVersionAliasValue(String alias, String version) {
        if (!VersionAliasRules.isValidValue(version)) {
            throw new VersionAliasCommandException(
                    "Invalid version for [versions]."
                            + alias
                            + ". Use a non-empty literal version string; Zolt does not support interpolation, dynamic versions, version ranges, or SNAPSHOTs.");
        }
        return version;
    }

    private static List<String> versionAliasReferences(ProjectConfig config, String alias) {
        Set<String> references = new LinkedHashSet<>();
        for (DependencyMetadata metadata : config.dependencyMetadata().values()) {
            if (alias.equals(metadata.versionRef())) {
                references.add("[" + metadata.section() + "]." + metadata.coordinate());
            }
        }
        config.dependencyPolicy().constraints().values().stream()
                .filter(constraint -> constraint.versionRef().filter(alias::equals).isPresent())
                .map(constraint -> "[dependencyConstraints]." + constraint.coordinate())
                .forEach(references::add);
        config.build().generatedMainSources().stream()
                .flatMap(step -> step.openApi().toolVersionRef().stream())
                .filter(alias::equals)
                .findAny()
                .ifPresent(ignored -> references.add("[generated.openapiTool].versionRef"));
        config.build().generatedTestSources().stream()
                .flatMap(step -> step.openApi().toolVersionRef().stream())
                .filter(alias::equals)
                .findAny()
                .ifPresent(ignored -> references.add("[generated.openapiTool].versionRef"));
        return List.copyOf(references);
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

    private static final class VersionAliasCommandException extends RuntimeException {
        private VersionAliasCommandException(String message) {
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
