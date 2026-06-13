package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.TestRunException;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestSelection;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusFrameworkTestRunnerTest {
    @TempDir
    private Path projectDir;

    @Test
    void disabledQuarkusConfigDoesNotRunFrameworkTests() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> {
                    throw new AssertionError("Application model should not be written.");
                },
                request -> {
                    throw new AssertionError("Descriptor should not be written.");
                },
                () -> {
                    throw new AssertionError("Worker classpath should not be requested.");
                },
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new AssertionError("Worker should not run.");
                });

        assertTrue(runner.runIfEnabled(request(disabledConfig())).isEmpty());
    }

    @Test
    void enabledQuarkusConfigWritesDescriptorAndRunsWorker() {
        List<QuarkusTestRunnerRequest> descriptorRequests = new ArrayList<>();
        List<List<Path>> workerClasspaths = new ArrayList<>();
        Path applicationModel = projectDir.resolve("target/quarkus/test-application-model.dat");
        Path workerJar = Path.of("/zolt/zolt.jar");
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(applicationModel),
                request -> {
                    descriptorRequests.add(request);
                    return descriptor(request);
                },
                () -> List.of(workerJar),
                (javaExecutable, workerClasspath, descriptor) -> {
                    workerClasspaths.add(workerClasspath);
                    assertEquals(Path.of("/jdk/bin/java"), javaExecutable);
                    assertEquals(QuarkusTestRunnerRequest.RUNNER_MODE, descriptor.runnerMode());
                    return "Worker tests successful\n";
                });

        Optional<FrameworkTestRunResult> result = runner.runIfEnabled(request(quarkusConfig()));

        assertTrue(result.isPresent());
        assertEquals("Worker tests successful\n", result.orElseThrow().output());
        assertFalse(result.orElseThrow().supportsFrameworkTestAnnotations());
        assertEquals(1, result.orElseThrow().workerClasspathEntries());
        assertEquals(1, result.orElseThrow().discoveryScanRoots());
        assertEquals(List.of(workerJar), workerClasspaths.getFirst());
        QuarkusTestRunnerRequest descriptorRequest = descriptorRequests.getFirst();
        assertEquals(projectDir.toAbsolutePath().normalize(), descriptorRequest.projectDirectory());
        assertEquals(
                projectDir.resolve("target/classes").toAbsolutePath().normalize(),
                descriptorRequest.mainOutputDirectory());
        assertEquals(
                projectDir.resolve("target/test-classes").toAbsolutePath().normalize(),
                descriptorRequest.testOutputDirectory());
        assertEquals(applicationModel.toAbsolutePath().normalize(), descriptorRequest.serializedApplicationModel());
        assertEquals(
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties").toAbsolutePath().normalize(),
                descriptorRequest.bootstrapDescriptorFile());
        assertTrue(descriptorRequest.jbossLogManagerPresent());
        assertEquals(List.of("com.example.FastTest"), descriptorRequest.testSelection().classSelectors());
        assertEquals(1, descriptorRequest.testSelection().methodSelectors().size());
        assertEquals(List.of("-Ddemo=true"), descriptorRequest.jvmArguments().values());
        assertEquals(Map.of("APP_ENV", "test"), descriptorRequest.environment());
    }

    @Test
    void missingSerializedModelProducesActionableError() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.empty(),
                request -> {
                    throw new AssertionError("Descriptor should not be written without a serialized model.");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("serialized application model was not written"));
        assertTrue(exception.getMessage().contains("Run `zolt build`, then run `zolt test` again"));
    }

    @Test
    void hiddenBootstrapStackTraceFailsEvenWhenJUnitReportsSuccess() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure("""
                        java.lang.ClassCastException: class io.quarkus.builder.BuildChainBuilder cannot be cast to class io.quarkus.builder.BuildChainBuilder
                            at io.quarkus.test.junit.TestBuildChainFunction$1.accept(TestBuildChainFunction.java:51)

                        Test run finished after 41 ms
                        [         1 tests successful      ]

                        Tests passed
                        """));

        assertTrue(exception.getMessage().contains("Quarkus test bootstrap failed"));
        assertTrue(exception.getMessage().contains("early Quarkus test runner path"));
        assertTrue(exception.getMessage().contains("unsupported Quarkus test bootstrap shape"));
        assertTrue(exception.getMessage().contains("BuildChainBuilder"));
    }

    @Test
    void quarkusTestAnnotationFailsBeforeWorkerRuns() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");
        QuarkusFrameworkTestRunner runner = runner(false, "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("`@QuarkusTest` execution is not supported"));
        assertTrue(exception.getMessage().contains("dedicated Quarkus test runner"));
        assertTrue(exception.getMessage().contains("com/example/QuarkusHttpTest.class"));
    }

    @Test
    void quarkusTestAnnotationCanPassThroughWhenDescriptorSupportsIt() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");
        QuarkusFrameworkTestRunner runner = runner(true, "Worker tests successful\n");

        Optional<FrameworkTestRunResult> result = runner.runIfEnabled(request(quarkusConfig()));

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().supportsFrameworkTestAnnotations());
        assertEquals("Worker tests successful\n", result.orElseThrow().output());
    }

    private FrameworkTestRunRequest request(ProjectConfig config) {
        return new FrameworkTestRunRequest(
                projectDir,
                config,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                List.of(Path.of("/repo/jboss-logmanager-3.0.jar")),
                Path.of("/jdk/bin/java"),
                new FrameworkTestSelection(
                        List.of("com.example.FastTest"),
                        List.of(new FrameworkTestSelection.MethodSelector("com.example.FastTest", "runs")),
                        List.of("*Test"),
                        List.of("fast"),
                        List.of("slow")),
                List.of("-Ddemo=true"),
                Map.of("APP_ENV", "test"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(QuarkusTestRunnerRequest request) {
        return descriptor(request, QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    private static QuarkusTestRunnerDescriptor descriptor(
            QuarkusTestRunnerRequest request,
            boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                request.projectDirectory().resolve("target/quarkus/zolt-test-bootstrap.properties"),
                request.projectDirectory().resolve("target/quarkus/zolt-test-classpath.txt"),
                request.projectDirectory(),
                request.mainOutputDirectory(),
                request.testOutputDirectory(),
                request.serializedApplicationModel(),
                request.bootstrapDescriptorFile(),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                request.jbossLogManagerPresent(),
                request.testRuntimeClasspath(),
                request.testSelection(),
                request.jvmArguments(),
                request.environment());
    }

    private QuarkusFrameworkTestRunner runner(boolean supportsQuarkusTestAnnotations, String output) {
        return new QuarkusFrameworkTestRunner(
                (projectDirectory, config) ->
                        Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, supportsQuarkusTestAnnotations),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> output);
    }

    private static ProjectConfig quarkusConfig() {
        return baseConfig().withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                true,
                QuarkusPackageMode.FAST_JAR)));
    }

    private static ProjectConfig disabledConfig() {
        return baseConfig();
    }

    private static ProjectConfig baseConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());
    }
}
