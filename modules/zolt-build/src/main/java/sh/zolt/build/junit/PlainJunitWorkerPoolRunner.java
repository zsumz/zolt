package sh.zolt.build.junit;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.runtime.TestRunException;
import sh.zolt.build.profile.TestProfileMerger;
import sh.zolt.project.ProjectConfig;
import sh.zolt.test.TestInventoryEntry;
import sh.zolt.test.TestSelection;
import sh.zolt.test.shard.TestWorkerPoolPlan;
import sh.zolt.test.shard.TestWorkerPoolWave;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PlainJunitWorkerPoolRunner {
    private final PlainJunitWorkerRunner plainJunitWorkerRunner;

    public PlainJunitWorkerPoolRunner(PlainJunitWorkerRunner plainJunitWorkerRunner) {
        this.plainJunitWorkerRunner = plainJunitWorkerRunner;
    }

    public PlainJunitWorkerPoolRunResult run(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            ProjectConfig config,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestWorkerPoolPlan workerPoolPlan,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events,
            Optional<Path> profileDirectory) {
        ExecutorService executor = Executors.newFixedThreadPool(workerPoolPlan.maxWorkers());
        StringBuilder output = new StringBuilder();
        long startupNanos = 0L;
        long requestStarted = System.nanoTime();
        int workerRequests = 0;
        List<String> workerIds = workerIds(workerPoolPlan);
        writeWorkerEvidenceManifests(reportsDirectory, jvmArguments, workerIds);
        try {
            for (int waveIndex = 0; waveIndex < workerPoolPlan.waves().size(); waveIndex++) {
                TestWorkerPoolWave wave = workerPoolPlan.waves().get(waveIndex);
                List<Future<WorkerTaskResult>> futures = new ArrayList<>();
                for (int entryIndex = 0; entryIndex < wave.entries().size(); entryIndex++) {
                    TestInventoryEntry entry = wave.entries().get(entryIndex);
                    String workerId = "wave-" + (waveIndex + 1) + "-worker-" + (entryIndex + 1);
                    futures.add(executor.submit(workerTask(
                            javaExecutable,
                            workerClasspath,
                            projectDirectory,
                            config,
                            testRuntimeClasspath,
                            testOutputDirectory,
                            testSelection,
                            entry,
                            jvmArguments,
                            environment,
                            reportsDirectory,
                            events,
                            profileDirectory,
                            workerId)));
                }
                for (Future<WorkerTaskResult> future : futures) {
                    WorkerTaskResult taskResult = getWorkerTask(future);
                    workerRequests++;
                    startupNanos += taskResult.result().startupNanos();
                    output.append(taskResult.result().workerResult().output());
                    if (taskResult.result().workerResult().exitCode() != 0) {
                        throw new TestRunException(
                                "JUnit worker tests failed with exit code "
                                        + taskResult.result().workerResult().exitCode()
                                        + " in "
                                        + taskResult.className()
                                        + ". Fix failing tests, then run `zolt test` again.\n"
                                        + taskResult.result().workerResult().output().stripTrailing());
                    }
                }
            }
            profileDirectory.ifPresent(directory -> TestProfileMerger.mergeWorkerProfiles(directory, workerIds));
            return new PlainJunitWorkerPoolRunResult(
                    output.toString(),
                    workerRequests,
                    startupNanos,
                    System.nanoTime() - requestStarted);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<WorkerTaskResult> workerTask(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            ProjectConfig config,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestInventoryEntry entry,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events,
            Optional<Path> profileDirectory,
            String workerId) {
        return () -> {
            PlainJunitWorkerRunResult result = plainJunitWorkerRunner.run(
                    javaExecutable,
                    workerClasspath,
                    projectDirectory,
                    testRuntimeClasspath,
                    testOutputDirectory,
                    workerSelection(testSelection, entry),
                    workerJvmArguments(jvmArguments, workerId),
                    workerEnvironment(projectDirectory, config, environment, jvmArguments, workerId),
                    workerReportsDirectory(reportsDirectory, workerId),
                    events,
                    workerProfileDirectory(profileDirectory, workerId));
            return new WorkerTaskResult(entry.className(), result);
        };
    }

    private static WorkerTaskResult getWorkerTask(Future<WorkerTaskResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TestRunException("JUnit worker pool was interrupted while waiting for test results.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TestRunException testRunException) {
                throw testRunException;
            }
            throw new TestRunException("JUnit worker pool failed while running tests.", cause);
        }
    }

    private static TestSelection workerSelection(TestSelection selection, TestInventoryEntry entry) {
        List<TestSelection.MethodSelector> methodSelectors = selection.methodSelectors().stream()
                .filter(method -> method.className().equals(entry.className()))
                .toList();
        List<String> classSelectors = methodSelectors.isEmpty()
                ? List.of(entry.className())
                : List.of();
        return TestSelection.fromFields(
                classSelectors,
                methodSelectors,
                List.of(),
                selection.includedTags(),
                selection.excludedTags());
    }

    private static Map<String, String> workerEnvironment(
            Path projectDirectory,
            ProjectConfig config,
            Map<String, String> environment,
            TestJvmArguments jvmArguments,
            String workerId) {
        Map<String, String> values = new LinkedHashMap<>(environment);
        Path outputDirectory = projectDirectory.resolve(config.build().outputRoot())
                .resolve("test-workers")
                .resolve(workerId)
                .toAbsolutePath()
                .normalize();
        values.put("ZOLT_TEST_WORKER_ID", workerId);
        values.put("ZOLT_TEST_WORKER_OUTPUT_DIR", outputDirectory.toString());
        jacocoWorkerExecFile(jvmArguments, workerId)
                .ifPresent(path -> values.put("ZOLT_COVERAGE_EXEC_FILE", path.toString()));
        return Map.copyOf(values);
    }

    private static Optional<Path> workerReportsDirectory(Optional<Path> reportsDirectory, String workerId) {
        return reportsDirectory.map(directory -> directory.resolve("workers").resolve(workerId));
    }

    private static Optional<Path> workerProfileDirectory(Optional<Path> profileDirectory, String workerId) {
        return profileDirectory.map(directory -> directory.resolve("workers").resolve(workerId));
    }

    private static TestJvmArguments workerJvmArguments(TestJvmArguments jvmArguments, String workerId) {
        List<String> values = jvmArguments.values().stream()
                .map(argument -> rewriteJacocoDestfile(argument, workerId).orElse(argument))
                .toList();
        return new TestJvmArguments(values);
    }

    private static List<String> workerIds(TestWorkerPoolPlan workerPoolPlan) {
        List<String> workerIds = new ArrayList<>();
        for (int waveIndex = 0; waveIndex < workerPoolPlan.waves().size(); waveIndex++) {
            TestWorkerPoolWave wave = workerPoolPlan.waves().get(waveIndex);
            for (int entryIndex = 0; entryIndex < wave.entries().size(); entryIndex++) {
                workerIds.add("wave-" + (waveIndex + 1) + "-worker-" + (entryIndex + 1));
            }
        }
        return List.copyOf(workerIds);
    }

    private static void writeWorkerEvidenceManifests(
            Optional<Path> reportsDirectory,
            TestJvmArguments jvmArguments,
            List<String> workerIds) {
        reportsDirectory.ifPresent(directory -> writeWorkerEvidenceManifest(directory.resolve("workers").resolve("zolt-workers.json"), workerIds));
        jacocoExecFile(jvmArguments)
                .map(Path::getParent)
                .ifPresent(directory -> writeWorkerEvidenceManifest(directory.resolve("workers").resolve("zolt-workers.json"), workerIds));
    }

    private static void writeWorkerEvidenceManifest(Path manifest, List<String> workerIds) {
        try {
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, workerEvidenceJson(workerIds));
        } catch (IOException exception) {
            throw new TestRunException("Could not write test worker evidence manifest to " + manifest + ".", exception);
        }
    }

    private static String workerEvidenceJson(List<String> workerIds) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": 1,\n");
        json.append("  \"workers\": [\n");
        for (int index = 0; index < workerIds.size(); index++) {
            json.append("    \"").append(workerIds.get(index)).append("\"");
            if (index + 1 < workerIds.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static Optional<Path> jacocoWorkerExecFile(TestJvmArguments jvmArguments, String workerId) {
        return jvmArguments.values().stream()
                .map(argument -> jacocoWorkerExecFile(argument, workerId))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Path> jacocoExecFile(TestJvmArguments jvmArguments) {
        return jvmArguments.values().stream()
                .map(PlainJunitWorkerPoolRunner::jacocoExecFile)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> rewriteJacocoDestfile(String argument, String workerId) {
        Optional<Path> execFile = jacocoWorkerExecFile(argument, workerId);
        if (execFile.isEmpty()) {
            return Optional.empty();
        }
        int valueStart = argument.indexOf("destfile=") + "destfile=".length();
        int valueEnd = argument.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = argument.length();
        }
        return Optional.of(argument.substring(0, valueStart) + execFile.orElseThrow() + argument.substring(valueEnd));
    }

    private static Optional<Path> jacocoWorkerExecFile(String argument, String workerId) {
        Optional<Path> canonicalExecFile = jacocoExecFile(argument);
        if (canonicalExecFile.isEmpty()) {
            return Optional.empty();
        }
        Path execFile = canonicalExecFile.orElseThrow();
        Path parent = execFile.getParent();
        if (parent == null) {
            return Optional.empty();
        }
        Path workerExecFile = parent.resolve("workers").resolve(workerId).resolve(execFile.getFileName())
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(workerExecFile.getParent());
        } catch (IOException exception) {
            throw new TestRunException("Could not create worker coverage directory " + workerExecFile.getParent() + ".", exception);
        }
        return Optional.of(workerExecFile);
    }

    private static Optional<Path> jacocoExecFile(String argument) {
        if (argument == null || !argument.startsWith("-javaagent:")
                || !argument.toLowerCase(Locale.ROOT).contains("jacoco")) {
            return Optional.empty();
        }
        int valueStart = argument.indexOf("destfile=");
        if (valueStart < 0) {
            return Optional.empty();
        }
        valueStart += "destfile=".length();
        int valueEnd = argument.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = argument.length();
        }
        return Optional.of(Path.of(argument.substring(valueStart, valueEnd)));
    }

    private record WorkerTaskResult(
            String className,
            PlainJunitWorkerRunResult result) {
    }
}
