package com.zolt.cli.command.testcmd;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.testruntime.compile.TestCompileResult;
import com.zolt.build.testruntime.compile.TestCompileResultWithClasspaths;
import com.zolt.test.runtime.TestJvmArguments;
import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.test.runtime.TestRunException;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.build.testruntime.TestRunService;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.*;
import com.zolt.cli.command.CommandServiceBundles.CommandTestServices;
import com.zolt.cli.command.build.CommandBuildAttributes;
import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionException;
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
        name = "integration-test",
        description = "Compile and run integration tests from configured integration-test roots.")
public final class IntegrationTestCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final TestRunService testRunService;
    private final WorkspaceTestService workspaceTestService;
    private final CommandLockfiles lockfiles;

    @Option(names = "--workspace", description = "Run integration tests for workspace members in dependency order.")
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

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public IntegrationTestCommand() {
        this(
                new ZoltTomlParser(),
                CommandFrameworkServices.testCommandServices(),
                new CommandLockfiles());
    }

    IntegrationTestCommand(
            ZoltTomlParser tomlParser,
            CommandTestServices testServices,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                testServices.testRunService(),
                testServices.workspaceTestService(),
                lockfiles);
    }

    IntegrationTestCommand(
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
            TestJvmArguments testJvmArguments = TestJvmArguments.fromCli(jvmArgs);
            List<String> requestedTestEvents = CommandTestEvents.validated(testEvents);
            if (workspace) {
                runWorkspaceIntegrationTests(
                        projectRoot,
                        timings,
                        testSelection,
                        testJvmArguments,
                        TestReportSettings.reportsDirectory(workspaceReportsDir()),
                        requestedTestEvents);
                return;
            }
            runSingleProjectIntegrationTests(projectRoot, timings, testSelection, testJvmArguments, requestedTestEvents);
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
            CommandTimings.print(spec, "integration-test", projectRoot, timingOptions, timings);
        }
    }

    private void runWorkspaceIntegrationTests(
            Path projectRoot,
            TimingRecorder timings,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            TestReportSettings reportSettings,
            List<String> requestedTestEvents) {
        lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
        WorkspaceTestResult result = timings.measure(
                "integration-test workspace",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace integration tests",
                            () -> workspaceTestService.planTests(
                                    projectRoot,
                                    cacheRoot,
                                    CommandWorkspaceSelections.from(all, members, memberGroups)),
                            CommandBuildAttributes::workspaceBuildPlan);
                    WorkspaceBuildResult buildResult = timings.measure(
                            "build workspace integration-test inputs",
                            () -> workspaceTestService.buildTestInputs(plan, cacheRoot),
                            build -> CommandBuildAttributes.workspaceBuild(build, plan.selection()));
                    return timings.measure(
                            "run workspace integration-test members",
                            () -> workspaceTestService.runIntegrationTests(
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
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        for (WorkspaceTestResult.MemberTestRunResult member : result.members()) {
            CommandOutput.printAndFlush(spec, member.result().output());
            if (!member.result().output().isEmpty() && !member.result().output().endsWith("\n")) {
                output.blankLine();
            }
            output.success("Integration tests passed in " + member.member());
            member.result().reportsDirectory().ifPresent(directory ->
                    output.pointer("wrote", directory.toString()));
        }
        output.summary(
                "Integration tests passed for " + result.members().size() + " workspace members",
                result.members().size() + " members");
    }

    private void runSingleProjectIntegrationTests(
            Path projectRoot,
            TimingRecorder timings,
            TestSelection testSelection,
            TestJvmArguments testJvmArguments,
            List<String> requestedTestEvents) {
        ProjectConfig config = timings.measure(
                "config read",
                () -> tomlParser.parse(projectRoot.resolve("zolt.toml")));
        lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, false);
        ProjectConfig integrationConfig = config.withBuildSettings(config.build().asIntegrationTestBuild());
        TestReportSettings reportSettings = TestReportSettings.reportsDirectory(integrationReportsDir(config));
        TestRunResult result = timings.measure(
                "run integration tests",
                () -> {
                    TestCompileResultWithClasspaths compileResult = timings.measure(
                            "compile integration tests",
                            () -> {
                                BuildResultWithClasspaths buildResult = timings.measure(
                                        "build integration-test inputs",
                                        () -> testRunService.buildTestInputs(
                                                projectRoot,
                                                integrationConfig,
                                                cacheRoot),
                                        resultWithClasspaths -> CommandBuildAttributes.build(
                                                resultWithClasspaths.buildResult()));
                                TestCompileResult testCompileResult = timings.measure(
                                        "compile integration-test sources",
                                        () -> testRunService.compileTests(
                                                projectRoot,
                                                integrationConfig,
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
                            "execute integration tests",
                            () -> testRunService.runCompiledTests(
                                    projectRoot,
                                    integrationConfig,
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
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (!result.output().isEmpty() && !result.output().endsWith("\n")) {
            output.blankLine();
        }
        output.summary(
                "Integration tests passed",
                result.compileResult().sourceCount() + " test source files");
        result.reportsDirectory().ifPresent(directory ->
                output.pointer("wrote", directory.toString()));
    }

    private Path integrationReportsDir(ProjectConfig config) {
        return reportsDir == null
                ? Path.of(config.build().outputRoot()).resolve("integration-test-reports")
                : reportsDir;
    }

    private Path workspaceReportsDir() {
        return reportsDir == null ? Path.of("target/integration-test-reports") : reportsDir;
    }
}
