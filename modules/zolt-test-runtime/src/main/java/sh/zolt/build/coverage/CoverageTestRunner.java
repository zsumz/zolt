package sh.zolt.build.coverage;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.build.testruntime.TestReportSettings;
import sh.zolt.build.testruntime.TestRunResult;
import sh.zolt.project.ProjectConfig;
import sh.zolt.test.TestSelection;
import sh.zolt.test.shard.TestShardSpec;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
interface CoverageTestRunner {
    TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard);
}
