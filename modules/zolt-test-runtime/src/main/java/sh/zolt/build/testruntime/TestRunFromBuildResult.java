package sh.zolt.build.testruntime;

import sh.zolt.build.BuildResult;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.project.ProjectConfig;
import sh.zolt.test.TestSelection;
import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.shard.TestShardSpec;
import java.nio.file.Path;
import java.util.List;

/** Compiles tests from a prebuilt {@link BuildResult} and runs them, for the build-then-test entry paths. */
final class TestRunFromBuildResult {
    private TestRunFromBuildResult() {
    }

    static TestRunResult run(
            TestRunService service,
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
        return service.runCompiledTests(
                projectDirectory,
                config,
                classpaths,
                service.compileTests(projectDirectory, config, classpaths, buildResult),
                selection,
                jvmArguments,
                reportSettings,
                cliEvents,
                suiteName,
                shard);
    }
}
