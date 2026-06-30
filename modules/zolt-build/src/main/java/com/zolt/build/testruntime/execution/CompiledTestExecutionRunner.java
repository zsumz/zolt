package com.zolt.build.testruntime.execution;

import com.zolt.classpath.ClasspathSet;
import com.zolt.build.profile.TestProfileSettings;
import com.zolt.build.testruntime.compile.TestCompileResult;
import com.zolt.build.testruntime.TestJvmArguments;
import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.project.ProjectConfig;
import com.zolt.test.TestSelection;
import com.zolt.test.shard.TestShardSpec;
import com.zolt.test.TestSuiteExecutionPlan;
import com.zolt.test.TestSuitePlanner;
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
