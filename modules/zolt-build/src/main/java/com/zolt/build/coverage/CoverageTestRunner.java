package com.zolt.build.coverage;

import com.zolt.test.runtime.TestJvmArguments;
import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.project.ProjectConfig;
import com.zolt.test.TestSelection;
import com.zolt.test.shard.TestShardSpec;
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
