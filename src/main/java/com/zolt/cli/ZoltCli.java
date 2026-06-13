package com.zolt.cli;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.BuildService;
import com.zolt.build.CompileDiagnostics;
import com.zolt.build.CoverageException;
import com.zolt.build.CoverageReportSettings;
import com.zolt.build.CoverageResult;
import com.zolt.build.CoverageService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
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
import com.zolt.cli.command.AddCommand;
import com.zolt.cli.command.CheckCommand;
import com.zolt.cli.command.ClasspathCommand;
import com.zolt.cli.command.CleanCommand;
import com.zolt.cli.command.ConflictsCommand;
import com.zolt.cli.command.DoctorCommand;
import com.zolt.cli.command.ExplainCommand;
import com.zolt.cli.command.IdeCommand;
import com.zolt.cli.command.InitCommand;
import com.zolt.cli.command.NativeCommand;
import com.zolt.cli.command.NativeSmokeCommand;
import com.zolt.cli.command.PlanCommand;
import com.zolt.cli.command.PlatformCommand;
import com.zolt.cli.command.PolicyCommand;
import com.zolt.cli.command.PublishCommand;
import com.zolt.cli.command.QuarkusCommand;
import com.zolt.cli.command.ReleaseArchiveCommand;
import com.zolt.cli.command.ReleaseVerifyCommand;
import com.zolt.cli.command.RemoveCommand;
import com.zolt.cli.command.ResolveCommand;
import com.zolt.cli.command.SelfCheckCommand;
import com.zolt.cli.command.SelfParityCommand;
import com.zolt.cli.command.TimingAttributeKeys;
import com.zolt.cli.command.TreeCommand;
import com.zolt.cli.command.UpdateCommand;
import com.zolt.cli.command.VersionCommand;
import com.zolt.cli.command.WhyCommand;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.perf.TimingFormat;
import com.zolt.perf.TimingFormatter;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusAugmentationResult;
import com.zolt.quarkus.QuarkusBuildAugmentationService;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
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
import com.zolt.workspace.WorkspaceSelection;
import com.zolt.workspace.WorkspaceSelectionRequest;
import com.zolt.workspace.WorkspaceTestResult;
import com.zolt.workspace.WorkspaceTestService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                ZoltCli.BuildCommand.class,
                ZoltCli.RunCommand.class,
                ZoltCli.TestCommand.class,
                ZoltCli.CoverageCommand.class,
                ZoltCli.PackageCommand.class,
                PublishCommand.class,
                ZoltCli.RunPackageCommand.class,
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
                                        build -> workspaceBuildAttributes(build, plan.selection()));
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
                                TimingAttributeKeys.EXEC_FILE, coverageResult.execFile().toString(),
                                TimingAttributeKeys.XML_REPORT, coverageResult.xmlReport().map(Path::toString).orElse("disabled"),
                                TimingAttributeKeys.HTML_DIRECTORY, coverageResult.htmlDirectory().map(Path::toString).orElse("disabled")));
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

    private static Map<String, String> buildAttributes(BuildResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(TimingAttributeKeys.RESOURCE_FILES, Integer.toString(result.resourceCount()));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.mainIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.mainCompileDiagnostics());
        addMainFingerprintAttributes(attributes, result);
        return attributes;
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
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(result.mainCompilationSkippedCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(result.mainCompilationExecutedCount()));
        addMainCompileDiagnostics(attributes, result.mainCompileDiagnostics());
        attributes.put(TimingAttributeKeys.WORKSPACE_ABI_INVALIDATIONS, Integer.toString(result.workspaceAbiInvalidationCount()));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
        return attributes;
    }

    private static Map<String, String> workspaceBuildAttributes(
            WorkspaceBuildResult result,
            WorkspaceSelection selection) {
        Map<String, String> attributes = workspaceBuildAttributes(result);
        addWorkspaceSelectionAttributes(attributes, selection);
        return attributes;
    }

    private static Map<String, String> workspaceBuildPlanAttributes(WorkspaceBuildPlan plan) {
        return Map.of(
                TimingAttributeKeys.INCLUDED_MEMBERS, Integer.toString(plan.selection().includedMembers().size()),
                TimingAttributeKeys.SELECTED_MEMBERS, Integer.toString(plan.selection().selectedMembers().size()),
                TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(plan.resolvedLockfile()));
    }

    private static Map<String, String> testRunAttributes(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.compileResult().buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.compileResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_RESOURCE_FILES, Integer.toString(result.compileResult().resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.compileResult().buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.compileResult().buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.compileResult().buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_SKIPPED, Boolean.toString(result.compileResult().testCompilationSkipped()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_MODE, result.compileResult().testCompilationMode());
        attributes.put(TimingAttributeKeys.TEST_INCREMENTAL_FALLBACK_REASON, result.compileResult().testIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.compileResult().buildResult().mainCompileDiagnostics());
        addTestCompileDiagnostics(attributes, result.compileResult().testCompileDiagnostics());
        attributes.put(TimingAttributeKeys.TEST_RUNNER, result.testRunner());
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put(TimingAttributeKeys.TEST_JVM_ARGS, Integer.toString(result.testJvmArguments().values().size()));
        addMainFingerprintAttributes(attributes, result.compileResult().buildResult());
        addTestFingerprintAttributes(attributes, result.compileResult());
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.output().length()));
        return attributes;
    }

    private static Map<String, String> testCompileAttributes(TestCompileResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_RESOURCE_FILES, Integer.toString(result.resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_SKIPPED, Boolean.toString(result.testCompilationSkipped()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_MODE, result.testCompilationMode());
        attributes.put(TimingAttributeKeys.TEST_INCREMENTAL_FALLBACK_REASON, result.testIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.buildResult().mainCompileDiagnostics());
        addTestCompileDiagnostics(attributes, result.testCompileDiagnostics());
        addMainFingerprintAttributes(attributes, result.buildResult());
        addTestFingerprintAttributes(attributes, result);
        return attributes;
    }

    private static Map<String, String> testExecutionAttributes(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.TEST_RUNNER, result.testRunner());
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put(TimingAttributeKeys.TEST_JVM_ARGS, Integer.toString(result.testJvmArguments().values().size()));
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.output().length()));
        return attributes;
    }

    private static void addPlainJunitWorkerTimingAttributes(Map<String, String> attributes, TestRunResult result) {
        if (result.testRunnerStartupNanos() >= 0L) {
            attributes.put(TimingAttributeKeys.TEST_RUNNER_STARTUP_MILLIS, Long.toString(result.testRunnerStartupNanos() / 1_000_000L));
            attributes.put(TimingAttributeKeys.TEST_RUNNER_STARTUP_NANOS, Long.toString(result.testRunnerStartupNanos()));
        }
        if (result.testRunnerRequestNanos() >= 0L) {
            attributes.put(TimingAttributeKeys.TEST_RUNNER_REQUEST_MILLIS, Long.toString(result.testRunnerRequestNanos() / 1_000_000L));
            attributes.put(TimingAttributeKeys.TEST_RUNNER_REQUEST_NANOS, Long.toString(result.testRunnerRequestNanos()));
        }
    }

    private static void addMainCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        addCompileDiagnostics(attributes, TimingAttributeKeys.MAIN_PREFIX, diagnostics);
    }

    private static void addTestCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        addCompileDiagnostics(attributes, TimingAttributeKeys.TEST_PREFIX, diagnostics);
    }

    private static void addCompileDiagnostics(
            Map<String, String> attributes,
            String prefix,
            CompileDiagnostics diagnostics) {
        CompileDiagnostics values = diagnostics == null ? CompileDiagnostics.empty() : diagnostics;
        attributes.put(prefix + TimingAttributeKeys.SOURCES_ADDED_SUFFIX, Integer.toString(values.sourcesAdded()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_CHANGED_SUFFIX, Integer.toString(values.sourcesChanged()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_DELETED_SUFFIX, Integer.toString(values.sourcesDeleted()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_RECOMPILED_SUFFIX, Integer.toString(values.sourcesRecompiled()));
        attributes.put(
                prefix + TimingAttributeKeys.DEPENDENT_SOURCES_RECOMPILED_SUFFIX,
                Integer.toString(values.dependentSourcesRecompiled()));
        attributes.put(prefix + TimingAttributeKeys.CLASSES_DELETED_SUFFIX, Integer.toString(values.classesDeleted()));
        attributes.put(prefix + TimingAttributeKeys.ABI_CHANGED_CLASSES_SUFFIX, Integer.toString(values.abiChangedClasses()));
        attributes.put(
                prefix + TimingAttributeKeys.PACKAGE_PRIVATE_ABI_CHANGED_CLASSES_SUFFIX,
                Integer.toString(values.packagePrivateAbiChangedClasses()));
    }

    private static void addMainFingerprintAttributes(Map<String, String> attributes, BuildResult result) {
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
    }

    private static void addMainFingerprintAttributes(
            Map<String, String> attributes,
            long checkNanos,
            long writeNanos) {
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_CHECK_MILLIS, Long.toString(checkNanos / 1_000_000L));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_CHECK_NANOS, Long.toString(checkNanos));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_WRITE_MILLIS, Long.toString(writeNanos / 1_000_000L));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_WRITE_NANOS, Long.toString(writeNanos));
    }

    private static void addTestFingerprintAttributes(Map<String, String> attributes, TestCompileResult result) {
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_MILLIS, Long.toString(result.testFingerprintCheckMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_NANOS, Long.toString(result.testFingerprintCheckNanos()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_MILLIS, Long.toString(result.testFingerprintWriteMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_NANOS, Long.toString(result.testFingerprintWriteNanos()));
    }

    private static void addTestSelectionAttributes(Map<String, String> attributes, TestSelection selection) {
        attributes.put(TimingAttributeKeys.TEST_CLASS_SELECTORS, Integer.toString(selection.classSelectors().size()));
        attributes.put(TimingAttributeKeys.TEST_METHOD_SELECTORS, Integer.toString(selection.methodSelectors().size()));
        attributes.put(TimingAttributeKeys.TEST_PATTERNS, Integer.toString(selection.classNamePatterns().size()));
        attributes.put(TimingAttributeKeys.TEST_INCLUDED_TAGS, Integer.toString(selection.includedTags().size()));
        attributes.put(TimingAttributeKeys.TEST_EXCLUDED_TAGS, Integer.toString(selection.excludedTags().size()));
    }

    private static Map<String, String> workspaceTestAttributes(WorkspaceTestResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.INCLUDED_MEMBERS, Integer.toString(result.includedMemberCount()));
        attributes.put(TimingAttributeKeys.SELECTED_MEMBERS, Integer.toString(result.selectedMemberCount()));
        attributes.put(TimingAttributeKeys.DEPENDENCY_MEMBERS, Integer.toString(result.dependencyMemberCount()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.mainSourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.testSourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(result.mainCompilationSkippedCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(result.mainCompilationExecutedCount()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATIONS_SKIPPED, Integer.toString(result.testCompilationSkippedCount()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATIONS_EXECUTED, Integer.toString(result.testCompilationExecutedCount()));
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntryCount()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntryCount()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRootCount()));
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_MILLIS, Long.toString(result.testFingerprintCheckMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_NANOS, Long.toString(result.testFingerprintCheckNanos()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_MILLIS, Long.toString(result.testFingerprintWriteMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_NANOS, Long.toString(result.testFingerprintWriteNanos()));
        attributes.put(TimingAttributeKeys.TEST_CLASS_SELECTORS, Integer.toString(result.testClassSelectorCount()));
        attributes.put(TimingAttributeKeys.TEST_METHOD_SELECTORS, Integer.toString(result.testMethodSelectorCount()));
        attributes.put(TimingAttributeKeys.TEST_PATTERNS, Integer.toString(result.testPatternCount()));
        attributes.put(TimingAttributeKeys.TEST_INCLUDED_TAGS, Integer.toString(result.testIncludedTagCount()));
        attributes.put(TimingAttributeKeys.TEST_EXCLUDED_TAGS, Integer.toString(result.testExcludedTagCount()));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        return attributes;
    }

    private static void addWorkspaceSelectionAttributes(
            Map<String, String> attributes,
            WorkspaceSelection selection) {
        attributes.put(TimingAttributeKeys.INCLUDED_MEMBERS, Integer.toString(selection.includedMembers().size()));
        attributes.put(TimingAttributeKeys.SELECTED_MEMBERS, Integer.toString(selection.selectedMembers().size()));
        attributes.put(TimingAttributeKeys.DEPENDENCY_MEMBERS, Integer.toString(selection.includedMembers().size() - selection.selectedMembers().size()));
    }

    private static Map<String, String> runAttributes(RunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_CLASS, result.javaRunResult().mainClass());
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.RESOURCE_FILES, Integer.toString(result.buildResult().resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.buildResult().mainIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.buildResult().mainCompileDiagnostics());
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.buildResult().resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    private static Map<String, String> workspaceRunAttributes(WorkspaceRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(workspaceRunSourceCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(workspaceRunMainCompilationSkippedCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(workspaceRunMainCompilationExecutedCount(result)));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(workspaceRunOutputBytes(result)));
        return attributes;
    }

    private static Map<String, String> packageAttributes(PackageResult result) {
        return Map.of(
                TimingAttributeKeys.MODE, result.mode().configValue(),
                TimingAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                TimingAttributeKeys.HAS_MAIN_CLASS, Boolean.toString(result.hasMainClass()),
                TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.buildResult().resolvedLockfile()));
    }

    private static Map<String, String> workspacePackageAttributes(WorkspacePackageResult result) {
        return Map.of(
                TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()),
                TimingAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
    }

    private static Map<String, String> runPackageAttributes(RunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MODE, result.packageResult().mode().configValue());
        attributes.put(TimingAttributeKeys.ENTRIES, Integer.toString(result.packageResult().entryCount()));
        attributes.put(TimingAttributeKeys.HAS_MAIN_CLASS, Boolean.toString(result.packageResult().hasMainClass()));
        attributes.put(TimingAttributeKeys.MAIN_CLASS, result.javaRunResult().mainClass());
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.packageResult().buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.packageResult().buildResult().mainCompilationMode());
        attributes.put(
                TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON,
                result.packageResult().buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.packageResult().buildResult().resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    private static Map<String, String> workspaceRunPackageAttributes(WorkspaceRunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(workspaceRunPackageSourceCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(workspaceRunPackageMainCompilationSkippedCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(workspaceRunPackageMainCompilationExecutedCount(result)));
        attributes.put(TimingAttributeKeys.ENTRIES, Integer.toString(workspaceRunPackageEntryCount(result)));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(workspaceRunPackageOutputBytes(result)));
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
            return Map.of(TimingAttributeKeys.ENABLED, "false");
        }
        QuarkusAugmentationResult augmentation = result.orElseThrow();
        return Map.of(
                TimingAttributeKeys.ENABLED, "true",
                TimingAttributeKeys.RUNNER_JAR, augmentation.workerResult().runnerJar().toString());
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
        attributes.put(TimingAttributeKeys.MODE, plan.mode().configValue());
        attributes.put(TimingAttributeKeys.DEPENDENCIES, String.valueOf(plan.dependencies().size()));
        attributes.put(TimingAttributeKeys.WARNINGS, String.valueOf(plan.warnings().size()));
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

}
