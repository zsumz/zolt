package sh.zolt.build.junit;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.runtime.TestRunException;
import sh.zolt.junit.JunitWorkerClient;
import sh.zolt.junit.JunitWorkerClientException;
import sh.zolt.junit.JunitWorkerProcess;
import sh.zolt.junit.JunitWorkerProcessLauncher;
import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PlainJunitWorkerProcessRunner {
    private PlainJunitWorkerProcessRunner() {
    }

    public static PlainJunitWorkerRunResult run(
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
            Optional<Path> profileDirectory) {
        long startupStarted = System.nanoTime();
        try (JunitWorkerProcess process = new JunitWorkerProcessLauncher(javaExecutable, workerClasspath)
                .start(projectDirectory, testRuntimeClasspath, jvmArguments.values(), environment)) {
            long startupNanos = System.nanoTime() - startupStarted;
            long requestStarted = System.nanoTime();
            JunitWorkerClient.WorkerRunResult result = process.run(
                    testOutputDirectory,
                    testSelection,
                    reportsDirectory,
                    events,
                    profileDirectory);
            long requestNanos = System.nanoTime() - requestStarted;
            return new PlainJunitWorkerRunResult(result, startupNanos, requestNanos);
        } catch (JunitWorkerClientException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }
}
