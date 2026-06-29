package com.zolt.build.junit;

import com.zolt.build.testruntime.TestJvmArguments;
import com.zolt.build.testruntime.TestRunException;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.junit.JunitWorkerClientException;
import com.zolt.junit.JunitWorkerProcess;
import com.zolt.junit.JunitWorkerProcessLauncher;
import com.zolt.test.TestSelection;
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
