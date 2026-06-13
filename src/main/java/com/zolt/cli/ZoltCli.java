package com.zolt.cli;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.CompileDiagnostics;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
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
import com.zolt.cli.command.BuildCommand;
import com.zolt.cli.command.CheckCommand;
import com.zolt.cli.command.ClasspathCommand;
import com.zolt.cli.command.CleanCommand;
import com.zolt.cli.command.ConflictsCommand;
import com.zolt.cli.command.CommandTestEvents;
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
import com.zolt.project.ProjectConfig;
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
import com.zolt.workspace.WorkspaceResolveService;
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
                BuildCommand.class,
                RunCommand.class,
                ZoltCli.TestCommand.class,
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
                List<String> requestedTestEvents = CommandTestEvents.validated(testEvents);
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

    private static void printAndFlush(CommandSpec spec, String output) {
        spec.commandLine().getOut().print(output);
        spec.commandLine().getOut().flush();
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
