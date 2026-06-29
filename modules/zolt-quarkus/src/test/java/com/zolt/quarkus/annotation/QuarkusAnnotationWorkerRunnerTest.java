package com.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.testworker.QuarkusTestWorkerPlan;
import com.zolt.quarkus.testworker.QuarkusTestWorkerPlanStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAnnotationWorkerRunnerTest extends QuarkusAnnotationWorkerRunnerTestSupport {
    @TempDir
    private Path tempDir;

    @Test
    void executesGeneratedLaunchRequest() {
        List<QuarkusAnnotationLaunchRequest> launchRequests = new java.util.ArrayList<>();
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(
                                        Path.of("/cache/io/quarkus/quarkus-core-3.33.2.jar"),
                                        Path.of("/cache/io/quarkus/quarkus-rest-3.33.2.jar"),
                                        Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> {
                            launchRequests.add(request);
                            return new QuarkusAnnotationJvmRunner.Result(0, "Quarkus annotation tests passed\n");
                        })
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(0, result.exitCode());
        assertEquals("Quarkus annotation tests passed\n", result.output());
        assertEquals(List.of("com.example.HttpTest"), launchRequests.getFirst().testClasses());
    }

    @Test
    void writesTestIndexBeforeLaunchingJvm() {
        List<String> events = new java.util.ArrayList<>();
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(
                                        Path.of("/cache/io/quarkus/quarkus-core-3.33.2.jar"),
                                        Path.of("/cache/io/quarkus/quarkus-rest-3.33.2.jar"),
                                        Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> {
                            events.add("launch:" + request.testClasses().getFirst());
                            return new QuarkusAnnotationJvmRunner.Result(0, "Quarkus annotation tests passed\n");
                        },
                        new QuarkusAnnotationClasspathSplitDiagnostic(),
                        request -> events.add("index:" + request.testClasses().getFirst()))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(0, result.exitCode());
        assertEquals(List.of("index:com.example.HttpTest", "launch:com.example.HttpTest"), events);
    }

    @Test
    void resetsAnnotationDiagnosticsBeforeLaunchingJvm() throws Exception {
        Path diagnosticFile = tempDir.resolve("target/quarkus/annotation-runner/test-class-bean-customizer.txt");
        Path graphFile = tempDir.resolve("target/quarkus/annotation-runner/build-chain.dot");
        Files.createDirectories(diagnosticFile.getParent());
        Files.writeString(diagnosticFile, "stale customizer output");
        Files.writeString(graphFile, "stale graph output");

        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of(
                                        "-Duser.dir=/repo",
                                        "-Dzolt.quarkus.test-class-bean-diagnostic-file=" + diagnosticFile,
                                        "-Dquarkus.builder.graph-output=" + graphFile),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> {
                            assertFalse(Files.exists(diagnosticFile));
                            assertFalse(Files.exists(graphFile));
                            return new QuarkusAnnotationJvmRunner.Result(0, "Quarkus annotation tests passed\n");
                        })
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(0, result.exitCode());
    }
}
