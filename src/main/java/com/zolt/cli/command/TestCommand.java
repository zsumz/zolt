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
import com.zolt.build.TestSelection;
import com.zolt.build.TestSelectionException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceTestResult;
import com.zolt.workspace.WorkspaceTestService;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "test",
        mixinStandardHelpOptions = true,
        description = "Compile and run tests, starting with JUnit support.")
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
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public TestCommand() {
        this(
                new ZoltTomlParser(),
                CommandFrameworkServices.testRunService(),
                CommandFrameworkServices.workspaceTestService(),
                new CommandLockfiles());
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
                runWorkspaceTests(
                        timings,
                        testSelection,
                        testJvmArguments,
                        reportSettings,
                        requestedTestEvents);
                return;
            }
            runSingleProjectTests(
                    timings,
                    testSelection,
                    testJvmArguments,
                    reportSettings,
                    requestedTestEvents);
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
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "test", workingDirectory, timingOptions, timings);
        }
    }

    private void runWorkspaceTests(
            TimingRecorder timings,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            List<String> requestedTestEvents) {
        lockfiles.requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
        WorkspaceTestResult result = timings.measure(
                "test workspace",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace tests",
                            () -> workspaceTestService.planTests(
                                    workingDirectory,
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
                                    requestedTestEvents),
                            CommandTestAttributes::workspaceTest);
                },
                CommandTestAttributes::workspaceTest);
        if (result.resolvedLockfile()) {
            spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspaceTestResult.MemberTestRunResult member : result.members()) {
            CommandOutput.printAndFlush(spec, member.result().output());
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
    }

    private void runSingleProjectTests(
            TimingRecorder timings,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            List<String> requestedTestEvents) {
        ProjectConfig config = timings.measure(
                "config read",
                () -> tomlParser.parse(workingDirectory.resolve("zolt.toml")));
        lockfiles.requireFreshLockfile(workingDirectory, config, cacheRoot, false);
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
                                        resultWithClasspaths -> CommandBuildAttributes.build(
                                                resultWithClasspaths.buildResult()));
                                TestCompileResult testCompileResult = timings.measure(
                                        "compile test sources",
                                        () -> testRunService.compileTests(
                                                workingDirectory,
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
                                    workingDirectory,
                                    config,
                                    compileResult.classpaths(),
                                    compileResult.testCompileResult(),
                                    testSelection,
                                    testJvmArguments,
                                    reportSettings,
                                    requestedTestEvents),
                            CommandTestAttributes::testExecution);
                },
                CommandTestAttributes::testRun);
        CommandOutput.printAndFlush(spec, result.output());
        if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
            spec.commandLine().getOut().println();
        }
        spec.commandLine().getOut().println("Tests passed");
        result.reportsDirectory().ifPresent(directory ->
                spec.commandLine().getOut().println("Wrote test reports to " + directory));
    }
}
