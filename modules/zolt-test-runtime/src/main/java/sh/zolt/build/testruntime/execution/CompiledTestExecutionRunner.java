package sh.zolt.build.testruntime.execution;

import sh.zolt.classpath.ClasspathSet;
import sh.zolt.build.profile.TestProfileSettings;
import sh.zolt.build.testruntime.compile.TestCompileResult;
import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.build.testruntime.TestReportSettings;
import sh.zolt.build.testruntime.TestRunResult;
import sh.zolt.project.ProjectConfig;
import sh.zolt.test.TestSelection;
import sh.zolt.test.shard.TestShardSpec;
import sh.zolt.test.TestSuiteExecutionPlan;
import sh.zolt.test.TestSuitePlanner;
import java.nio.file.Path;
import java.util.List;

public final class CompiledTestExecutionRunner {
    private final CompiledTestRunner compiledTestRunner;
    private final TestSuitePlanner testSuitePlanner = new TestSuitePlanner();

    public CompiledTestExecutionRunner(CompiledTestRunner compiledTestRunner) {
        this.compiledTestRunner = compiledTestRunner;
    }

    public TestRunResult run(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard,
            TestProfileSettings profileSettings) {
        TestSuiteExecutionPlan executionPlan =
                testSuitePlanner.executionPlan(projectDirectory, config, suiteName, selection, shard);
        TestReportSettings effectiveReportSettings = (reportSettings == null ? TestReportSettings.disabled() : reportSettings)
                .forShard(suiteName, shard);
        TestProfileSettings effectiveProfileSettings = profileSettings == null
                ? TestProfileSettings.disabled()
                : profileSettings.forSuite(suiteName).forShard(suiteName, shard);
        return compiledTestRunner.run(
                projectDirectory,
                config,
                classpaths,
                compileResult,
                executionPlan.selection(),
                executionPlan.workerPoolPlan(),
                jvmArguments,
                effectiveReportSettings,
                cliEvents,
                effectiveProfileSettings);
    }
}
