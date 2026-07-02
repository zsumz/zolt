package sh.zolt.build.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.junit.JunitWorkerClient;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.test.TestInventoryEntry;
import sh.zolt.test.TestSelection;
import sh.zolt.test.shard.TestWorkerPoolPlan;
import sh.zolt.test.shard.TestWorkerPoolWave;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlainJunitWorkerPoolRunnerTest {
    @TempDir
    private Path tempDir;

    @Test
    void profiledWorkerPoolWritesWorkerLocalProfilesAndMergedProfile() throws IOException {
        List<Optional<Path>> profileDirectories = Collections.synchronizedList(new ArrayList<>());
        PlainJunitWorkerPoolRunner runner = new PlainJunitWorkerPoolRunner(
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    profileDirectories.add(profileDirectory);
                    Path profile = profileDirectory.orElseThrow();
                    String workerId = environment.get("ZOLT_TEST_WORKER_ID");
                    writeProfile(profile, workerProfile(workerId, selectedClass(testSelection)));
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("ok " + workerId + "\n", 0),
                            10L,
                            20L);
                });
        Path profileRoot = tempDir.resolve("target/test-profile");

        PlainJunitWorkerPoolRunResult result = runner.run(
                Path.of("java"),
                List.of(Path.of("zolt-worker.jar")),
                tempDir,
                config(),
                List.of(tempDir.resolve("target/classes")),
                tempDir.resolve("target/test-classes"),
                TestSelection.empty(),
                workerPoolPlan(),
                TestJvmArguments.empty(),
                Map.of(),
                Optional.empty(),
                List.of(),
                Optional.of(profileRoot));

        assertEquals(2, result.workerRequests());
        assertEquals(Set.of(
                profileRoot.resolve("workers/wave-1-worker-1"),
                profileRoot.resolve("workers/wave-1-worker-2")), profileDirectories.stream()
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet()));
        assertTrue(Files.exists(profileRoot.resolve("workers/wave-1-worker-1/profile.json")));
        assertTrue(Files.exists(profileRoot.resolve("workers/wave-1-worker-2/profile.json")));
        String merged = Files.readString(profileRoot.resolve("profile.json"));
        assertTrue(merged.contains("\"testsFound\": 2"));
        assertTrue(merged.contains("\"testsSucceeded\": 2"));
        assertTrue(merged.contains("\"className\": \"com.example.AlphaTest\""));
        assertTrue(merged.contains("\"className\": \"com.example.BetaTest\""));
        assertTrue(merged.contains("\"workerId\": \"wave-1-worker-1\""));
        assertTrue(merged.contains("\"workerId\": \"wave-1-worker-2\""));
        assertTrue(merged.contains("\"project\": \"demo\""));
        assertTrue(merged.contains("\"member\": \"apps/demo\""));
        assertTrue(merged.contains("\"suite\": \"fast\""));
        assertTrue(merged.contains("\"shard\": \"1/2\""));
        assertTrue(result.output().contains("ok wave-1-worker-1"));
        assertTrue(result.output().contains("ok wave-1-worker-2"));
    }

    private static TestWorkerPoolPlan workerPoolPlan() {
        return new TestWorkerPoolPlan(true, 2, List.of(new TestWorkerPoolWave(List.of(
                entry("com.example.AlphaTest"),
                entry("com.example.BetaTest")), Map.of())));
    }

    private static TestInventoryEntry entry(String className) {
        Path outputRoot = Path.of("target/test-classes");
        return new TestInventoryEntry(
                className,
                outputRoot,
                outputRoot.resolve(className.replace('.', '/') + ".class"),
                List.of("*Test"),
                "junit-jupiter",
                List.of());
    }

    private static String selectedClass(TestSelection selection) {
        return selection.classSelectors().getFirst();
    }

    private static void writeProfile(Path profileDirectory, String profileJson) {
        try {
            Files.createDirectories(profileDirectory);
            Files.writeString(profileDirectory.resolve("profile.json"), profileJson);
        } catch (IOException exception) {
            throw new AssertionError("could not write worker profile fixture", exception);
        }
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private static String workerProfile(String workerId, String className) {
        return """
                {
                  "schemaVersion": 1,
                  "runner": "zolt-junit-worker",
                  "workerId": "%s",
                  "projectRoot": "/workspace",
                  "project": "demo",
                  "member": "apps/demo",
                  "suite": "fast",
                  "shard": "1/2",
                  "summary": {
                    "testsFound": 1,
                    "testsSucceeded": 1,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "testsAborted": 0,
                    "durationMillis": 100
                  },
                  "tests": [
                    {
                      "uniqueId": "%s#runs",
                      "engineId": "junit-jupiter",
                      "className": "%s",
                      "methodName": "runs",
                      "displayName": "runs()",
                      "status": "passed",
                      "durationMillis": 100,
                      "workerId": "%s",
                      "projectRoot": "/workspace",
                      "project": "demo",
                      "member": "apps/demo",
                      "suite": "fast",
                      "shard": "1/2"
                    }
                  ],
                  "containers": [
                    {
                      "uniqueId": "%s",
                      "engineId": "junit-jupiter",
                      "className": "%s",
                      "methodName": "",
                      "displayName": "%s",
                      "status": "passed",
                      "durationMillis": 100,
                      "workerId": "%s",
                      "projectRoot": "/workspace",
                      "project": "demo",
                      "member": "apps/demo",
                      "suite": "fast",
                      "shard": "1/2",
                      "testCount": 1
                    }
                  ]
                }
                """.formatted(workerId, className, className, workerId, className, className, className, workerId);
    }
}
