package com.zolt.build.testruntime;

import com.zolt.classpath.ClasspathSet;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.JavaRunner;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.build.junit.PlainJunitWorkerProcessRunner;
import com.zolt.build.junit.PlainJunitWorkerRunner;
import com.zolt.build.profile.TestProfileSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import com.zolt.test.TestShardSpec;
import com.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public final class TestRunService {
    private final TestCompileService testCompileService;
    private final CompiledTestExecutionRunner compiledTestExecutionRunner;

    public TestRunService() {
        this(new JdkDetector());
    }

    public TestRunService(JdkChecker jdkDetector) {
        this(jdkDetector, FrameworkTestRunner.none());
    }

    public TestRunService(FrameworkTestRunner frameworkTestRunner) {
        this(new JdkDetector(), frameworkTestRunner);
    }

    public TestRunService(JdkChecker jdkDetector, FrameworkTestRunner frameworkTestRunner) {
        this(jdkDetector, frameworkTestRunner, new ResolveService());
    }

    public TestRunService(FrameworkTestRunner frameworkTestRunner, ResolveService resolveService) {
        this(new JdkDetector(), frameworkTestRunner, resolveService);
    }

    public TestRunService(
            JdkChecker jdkDetector,
            FrameworkTestRunner frameworkTestRunner,
            ResolveService resolveService) {
        this(
                new TestCompileService(jdkDetector, resolveService),
                jdkDetector,
                new JavaRunner(),
                frameworkTestRunner,
                new CurrentWorkerClasspath()::discover,
                PlainJunitWorkerProcessRunner::run,
                Boolean.getBoolean("zolt.junit.worker"),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            FrameworkTestRunner frameworkTestRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.compiledTestExecutionRunner = new CompiledTestExecutionRunner(new CompiledTestRunner(
                jdkDetector,
                javaRunner,
                frameworkTestRunner,
                plainJunitWorkerClasspath,
                plainJunitWorkerRunner,
                plainJunitWorkerEnabled,
                pathSeparator));
    }

    public TestRunResult runTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return runTests(projectDirectory, config, cacheRoot, TestSelection.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection) {
        return runTests(projectDirectory, config, cacheRoot, selection, TestJvmArguments.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, TestReportSettings.disabled());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, List.of());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, "all");
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName) {
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, cliEvents, suiteName, null);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard) {
        return runTests(
                projectDirectory,
                config,
                cacheRoot,
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard,
                TestProfileSettings.disabled());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard,
            TestProfileSettings profileSettings) {
        TestCompileResultWithClasspaths compileResult =
                compileTests(projectDirectory, config, cacheRoot);
        return runCompiledTests(
                projectDirectory,
                config,
                compileResult.classpaths(),
                compileResult.testCompileResult(),
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard,
                profileSettings);
    }

    public TestCompileResultWithClasspaths compileTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return testCompileService.compileTestsWithClasspaths(projectDirectory, config, cacheRoot);
    }

    public BuildResultWithClasspaths buildTestInputs(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return testCompileService.buildTestInputs(projectDirectory, config, cacheRoot);
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, TestSelection.empty());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, TestJvmArguments.empty());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, TestReportSettings.disabled());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, List.of());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, cliEvents, "all");
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, cliEvents, suiteName, null);
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard) {
        return runCompiledTests(
                projectDirectory,
                config,
                classpaths,
                compileResult,
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard,
                TestProfileSettings.disabled());
    }

    public TestRunResult runCompiledTests(
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
        return compiledTestExecutionRunner.run(
                projectDirectory,
                config,
                classpaths,
                compileResult,
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard,
                profileSettings);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        return runTests(projectDirectory, config, classpaths, buildResult, selection, jvmArguments, reportSettings, cliEvents, "all", null);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard) {
        return runCompiledTests(
                projectDirectory,
                config,
                classpaths,
                compileTests(projectDirectory, config, classpaths, buildResult),
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard);
    }

    public TestCompileResult compileTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        return testCompileService.compileTests(projectDirectory, config, classpaths, buildResult);
    }

}
