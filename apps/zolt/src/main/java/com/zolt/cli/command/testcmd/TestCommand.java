package com.zolt.cli.command.testcmd;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.profile.TestProfileSettings;
import com.zolt.build.testruntime.*;
import com.zolt.build.testruntime.compile.TestCompileResult;
import com.zolt.build.testruntime.compile.TestCompileResultWithClasspaths;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.*;
import com.zolt.cli.command.CommandServiceBundles.CommandTestServices;
import com.zolt.cli.command.build.CommandBuildAttributes;
import com.zolt.cli.command.testplan.TestPlanCommand;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.test.runtime.TestJvmArguments;
import com.zolt.test.runtime.TestRunException;
import com.zolt.test.TestPlanException;
import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionException;
import com.zolt.test.shard.TestShardException;
import com.zolt.test.shard.TestShardSpec;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.service.WorkspaceBuildPlan;
import com.zolt.workspace.service.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.service.WorkspaceTestResult;
import com.zolt.workspace.service.WorkspaceTestService;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "test",
        description = "Compile and run tests, starting with JUnit support.",
        subcommands = {
                TestPlanCommand.class
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
    private CommandTestProfileOptions profileOptions = new CommandTestProfileOptions();

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
            TestProfileSettings profileSettings = profileOptions.settings();
            if (workspace) {
                runWorkspaceTests(
                        projectRoot,
                        timings,
                        CommandProgress.human(spec),
                        testSelection,
                        testJvmArguments,
                        reportSettings,
                        profileSettings,
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
                    profileSettings,
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
            TestProfileSettings profileSettings,
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
                                    shard,
                                    profileSettings),
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
                    output.pointer("wrote", directory.toString()));
        }
        result.profileDirectory().ifPresent(directory ->
                CommandTestProfileOutput.print(output, directory, profileSettings));
        int testedMembers = result.members().size();
        String summary = testedMembers < result.totalMemberCount()
                ? "Tested " + testedMembers + " of " + result.totalMemberCount()
                        + " workspace members; use --all to test every member"
                : "Tests passed for " + testedMembers + " workspace members";
        output.summary(summary, testedMembers + " members");
        output.provenance(CommandBuildProvenance.read(projectRoot));
        progress.result("Tested " + testedMembers + " workspace members");
    }

    private void runSingleProjectTests(
            Path projectRoot,
            TimingRecorder timings,
            ProgressWriter progress,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            TestProfileSettings profileSettings,
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
                                    shard,
                                    profileSettings),
                            CommandTestAttributes::testExecution);
                },
                CommandTestAttributes::testRun);
        CommandOutput.printAndFlush(spec, result.output());
        if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
            output.blankLine();
        }
        output.summary(
                "Tests passed",
                result.compileResult().sourceCount() + " test source files");
        result.reportsDirectory().ifPresent(directory ->
                output.pointer("wrote", directory.toString()));
        CommandTestProfileOutput.print(output, result, profileSettings);
        output.provenance(CommandBuildProvenance.read(projectRoot));
        progress.result("Tested project");
    }
}
