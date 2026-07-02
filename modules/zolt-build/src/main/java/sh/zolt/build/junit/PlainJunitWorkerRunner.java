package sh.zolt.build.junit;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@FunctionalInterface
public interface PlainJunitWorkerRunner {
    PlainJunitWorkerRunResult run(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events,
            Optional<Path> profileDirectory);
}
