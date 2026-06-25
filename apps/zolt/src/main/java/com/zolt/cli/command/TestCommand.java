package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResultWithClasspaths;
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
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.test.TestPlanException;
import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionException;
import com.zolt.test.TestShardException;
import com.zolt.test.TestShardPlan;
import com.zolt.test.TestShardSpec;
import com.zolt.test.TestSuitePlan;
import com.zolt.test.TestSuitePlanner;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceTestResult;
import com.zolt.workspace.WorkspaceTestService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "test",
        description = "Compile and run tests, starting with JUnit support.",
        subcommands = {
                TestCommand.PlanCommand.class
        })
public final class TestCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final TestRunService testRunService;
    private final WorkspaceTestService workspaceTestService;
    private final CommandLockfiles lockfiles;

    @Option(names = "--workspace", description = "Test workspace members in dependency order.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Option(names = "--suite", description = "Run one configured test suite. Defaults to all.")
    private String suiteName = "all";

    @Option(names = "--shard", description = "Run one deterministic test shard as index/total, such as 1/4.")
    private String shardValue;

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

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public TestCommand() {
        this(
                new ZoltTomlParser(),
                CommandFrameworkServices.testCommandServices(),
                new CommandLockfiles());
    }

    TestCommand(
            ZoltTomlParser tomlParser,
            CommandTestServices testServices,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                testServices.testRunService(),
                testServices.workspaceTestService(),
                lockfiles);
    }

    TestCommand(
            ZoltTomlParser tomlParser,
            TestRunService testRunService,
            WorkspaceTestService workspaceTestService,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.testRunService = testRunService;
        this.workspaceTestService = workspaceTestService;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        Path projectRoot = projectDirectory.path();
        try {
            TestSelection testSelection = TestSelection.fromCli(
                    testSelectors,
                    testPatterns,
                    includedTags,
                    excludedTags);
            TestShardSpec shard = TestShardSpec.parse(shardValue);
            TestJvmArguments testJvmArguments = TestJvmArguments.fromCli(jvmArgs);
            List<String> requestedTestEvents = CommandTestEvents.validated(testEvents);
            TestReportSettings reportSettings = TestReportSettings.reportsDirectory(reportsDir);
            if (workspace) {
                runWorkspaceTests(
                        projectRoot,
                        timings,
                        CommandProgress.human(spec),
                        testSelection,
                        testJvmArguments,
                        reportSettings,
                        requestedTestEvents,
                        suiteName,
                        shard);
                return;
            }
            runSingleProjectTests(
                    projectRoot,
                    timings,
                    CommandProgress.human(spec),
                    testSelection,
                    testJvmArguments,
                    reportSettings,
                    requestedTestEvents,
                    suiteName,
                    shard);
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | JavaRunException
                | ResourceCopyException
                | TestRunException
                | TestSelectionException
                | TestShardException
                | TestPlanException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "test", projectRoot, timingOptions, timings);
        }
    }

    private void runWorkspaceTests(
            Path projectRoot,
            TimingRecorder timings,
            ProgressWriter progress,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            List<String> requestedTestEvents,
            String suiteName,
            TestShardSpec shard) {
        lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
        progress.start("Testing workspace");
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        WorkspaceTestResult result = timings.measure(
                "test workspace",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace tests",
                            () -> workspaceTestService.planTests(
                                    projectRoot,
                                    cacheRoot,
                                    CommandWorkspaceSelections.from(all, members, memberGroups)),
                            CommandBuildAttributes::workspaceBuildPlan);
                    WorkspaceBuildResult buildResult = timings.measure(
                            "build workspace test inputs",
                            () -> workspaceTestService.buildTestInputs(plan, cacheRoot),
                            build -> CommandBuildAttributes.workspaceBuild(build, plan.selection()));
                    return timings.measure(
                            "run workspace test members",
                            () -> workspaceTestService.runTests(
                                    plan,
                                    buildResult,
                                    cacheRoot,
                                    testSelection,
                                    testJvmArguments,
                                    reportSettings,
                                    requestedTestEvents,
                                    suiteName,
                                    shard),
                            CommandTestAttributes::workspaceTest);
                },
                CommandTestAttributes::workspaceTest);
        if (result.resolvedLockfile()) {
            output.detail("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspaceTestResult.MemberTestRunResult member : result.members()) {
            CommandOutput.printAndFlush(spec, member.result().output());
            if (!member.result().output().isEmpty() && !member.result().output().endsWith("\n")) {
                output.blankLine();
            }
            output.success("Tests passed in " + member.member());
            member.result().reportsDirectory().ifPresent(directory ->
                    output.detail("Wrote test reports for "
                            + member.member()
                            + " to "
                            + directory));
        }
        output.success(
                "Tests passed for "
                        + result.members().size()
                        + " workspace members");
        progress.result("Tested " + result.members().size() + " workspace members");
    }

    private void runSingleProjectTests(
            Path projectRoot,
            TimingRecorder timings,
            ProgressWriter progress,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            List<String> requestedTestEvents,
            String suiteName,
            TestShardSpec shard) {
        ProjectConfig config = timings.measure(
                "config read",
                () -> tomlParser.parse(projectRoot.resolve("zolt.toml")));
        lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, false);
        progress.start("Testing project");
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.work("Testing " + config.project().name());
        TestRunResult result = timings.measure(
                "run tests",
                () -> {
                    TestCompileResultWithClasspaths compileResult = timings.measure(
                            "compile tests",
                            () -> {
                                BuildResultWithClasspaths buildResult = timings.measure(
                                        "build test inputs",
                                        () -> testRunService.buildTestInputs(
                                                projectRoot,
                                                config,
                                                cacheRoot),
                                        resultWithClasspaths -> CommandBuildAttributes.build(
                                                resultWithClasspaths.buildResult()));
                                TestCompileResult testCompileResult = timings.measure(
                                        "compile test sources",
                                        () -> testRunService.compileTests(
                                                projectRoot,
                                                config,
                                                buildResult.classpaths(),
                                                buildResult.buildResult()),
                                        CommandTestAttributes::testCompile);
                                return new TestCompileResultWithClasspaths(
                                        testCompileResult,
                                        buildResult.classpaths());
                            },
                            resultWithClasspaths -> CommandTestAttributes.testCompile(
                                    resultWithClasspaths.testCompileResult()));
                    return timings.measure(
                            "execute tests",
                            () -> testRunService.runCompiledTests(
                                    projectRoot,
                                    config,
                                    compileResult.classpaths(),
                                    compileResult.testCompileResult(),
                                    testSelection,
                                    testJvmArguments,
                                    reportSettings,
                                    requestedTestEvents,
                                    suiteName,
                                    shard),
                            CommandTestAttributes::testExecution);
                },
                CommandTestAttributes::testRun);
        CommandOutput.printAndFlush(spec, result.output());
        if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
            output.blankLine();
        }
        output.detail("Compiled " + result.compileResult().sourceCount() + " test source files");
        output.success("Tests passed");
        result.reportsDirectory().ifPresent(directory ->
                output.detail("Wrote test reports to " + directory));
        progress.result("Tested project");
    }

    @Command(name = "plan", description = "Show the selected test suite plan without executing tests.")
    public static final class PlanCommand implements Runnable {
        private final ZoltTomlParser tomlParser;
        private final TestSuitePlanner planner;

        @Option(names = "--suite", description = "Plan one configured test suite. Defaults to all.")
        private String suiteName = "all";

        @Option(names = "--shard-count", description = "Plan deterministic suite shards without executing tests.")
        private String shardCountValue;

        @Option(names = "--test", description = "Select one test class or method. May be repeated.")
        private List<String> testSelectors = List.of();

        @Option(names = "--tests", description = "Select test classes by glob-style class-name pattern. May be repeated.")
        private List<String> testPatterns = List.of();

        @Option(names = "--include-tag", description = "Include tests with a JUnit Platform tag. May be repeated.")
        private List<String> includedTags = List.of();

        @Option(names = "--exclude-tag", description = "Exclude tests with a JUnit Platform tag. May be repeated.")
        private List<String> excludedTags = List.of();

        @Mixin
        private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

        @Spec
        private CommandSpec spec;

        public PlanCommand() {
            this(new ZoltTomlParser(), new TestSuitePlanner());
        }

        PlanCommand(ZoltTomlParser tomlParser, TestSuitePlanner planner) {
            this.tomlParser = tomlParser;
            this.planner = planner;
        }

        @Override
        public void run() {
            Path projectRoot = projectDirectory.path();
            try {
                TestSelection selection = TestSelection.fromCli(
                        testSelectors,
                        testPatterns,
                        includedTags,
                        excludedTags);
                ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
                TestSuitePlan plan = planner.plan(projectRoot, config, suiteName, selection);
                int shardCount = TestShardSpec.parseShardCount(shardCountValue);
                printPlan(config, plan, shardCount == 0
                        ? List.of()
                        : planner.shardPlans(projectRoot, config, suiteName, selection, shardCount),
                        projectRoot);
            } catch (TestPlanException | TestSelectionException | TestShardException | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }

        private void printPlan(ProjectConfig config, TestSuitePlan plan, List<TestShardPlan> shards, Path projectRoot) {
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            output.line("Test plan for " + config.project().name());
            output.line("suite: " + plan.suiteName());
            output.line("configured suite: " + (plan.configuredSuite() ? "yes" : "no"));
            output.line("test output: " + plan.outputDirectory());
            output.line("matched entries: " + plan.entries().size());
            output.line("empty: " + (plan.empty() ? "yes" : "no"));
            printFilters(output, "class filters", plan.includeClassname(), plan.excludeClassname());
            printFilters(output, "tag filters", plan.includeTag(), plan.excludeTag());
            if (!plan.selectionClassname().isEmpty()
                    || !plan.selectionIncludeTag().isEmpty()
                    || !plan.selectionExcludeTag().isEmpty()) {
                printFilters(output, "selection class filters", plan.selectionClassname(), List.of());
                printFilters(output, "selection tag filters", plan.selectionIncludeTag(), plan.selectionExcludeTag());
            }
            printList(output, "entries", plan.entries().stream()
                    .map(entry -> entry.className())
                    .toList());
            printList(output, "missing explicit selectors", plan.missingExplicitClassSelectors());
            printOverlaps(output, plan.overlappingEntries());
            printList(output, "unassigned entries", plan.unassignedEntries());
            printShards(output, shards, projectRoot);
        }

        private static void printFilters(
                CommandHumanOutput output,
                String label,
                List<String> includes,
                List<String> excludes) {
            String include = includes.isEmpty() ? "<none>" : String.join(", ", includes);
            String exclude = excludes.isEmpty() ? "<none>" : String.join(", ", excludes);
            output.line(label + ": include " + include + "; exclude " + exclude);
        }

        private static void printList(CommandHumanOutput output, String label, List<String> values) {
            output.line(label + ": " + values.size());
            for (String value : values) {
                output.line("- " + value);
            }
        }

        private static void printOverlaps(CommandHumanOutput output, Map<String, List<String>> overlaps) {
            output.line("overlapping entries: " + overlaps.size());
            for (Map.Entry<String, List<String>> entry : overlaps.entrySet()) {
                output.line("- " + entry.getKey() + " also matches " + String.join(", ", entry.getValue()));
            }
        }

        private static void printShards(CommandHumanOutput output, List<TestShardPlan> shards, Path projectRoot) {
            if (shards.isEmpty()) {
                return;
            }
            output.line("shards: " + shards.size());
            for (TestShardPlan shard : shards) {
                output.line("- shard "
                        + shard.shard().label()
                        + ": "
                        + shard.entries().size()
                        + " entries, empty: "
                        + (shard.empty() ? "yes" : "no")
                        + ", manifest: "
                        + shard.projectRelativeManifestPath(projectRoot));
            }
        }
    }
}
