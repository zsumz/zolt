package com.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.framework.FrameworkTestRunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusFrameworkTestRunnerTest extends QuarkusFrameworkTestRunnerTestSupport {
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
    void exposesQuarkusTestRunnerOwnershipAndCliMetadata() {
        QuarkusFrameworkTestRunner runner = runner(false, "unused");

        assertTrue(runner.isEnabled(quarkusConfig()));
        assertFalse(runner.isEnabled(disabledConfig()));
        assertEquals("quarkus-test-worker", runner.testRunnerName());
        assertTrue(runner.unsupportedReportsMessage().orElseThrow().contains(
                "JUnit XML reports are not supported by the Quarkus plain-JUnit worker path yet."));
    }

    @Test
    void enabledQuarkusConfigWritesDescriptorAndRunsWorker() {
        List<QuarkusTestRunnerRequest> descriptorRequests = new ArrayList<>();
        List<List<java.nio.file.Path>> workerClasspaths = new ArrayList<>();
        java.nio.file.Path applicationModel = projectDir.resolve("target/quarkus/test-application-model.dat");
        java.nio.file.Path workerJar = java.nio.file.Path.of("/zolt/zolt.jar");
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(applicationModel),
                request -> {
                    descriptorRequests.add(request);
                    return descriptor(request);
                },
                () -> List.of(workerJar),
                (javaExecutable, workerClasspath, descriptor) -> {
                    workerClasspaths.add(workerClasspath);
                    assertEquals(java.nio.file.Path.of("/jdk/bin/java"), javaExecutable);
                    assertEquals(QuarkusTestRunnerRequest.RUNNER_MODE, descriptor.runnerMode());
                    return "Worker tests successful\n";
                });

        Optional<FrameworkTestRunResult> result = runner.runIfEnabled(request(quarkusConfig()));

        assertTrue(result.isPresent());
        assertEquals("Worker tests successful\n", result.orElseThrow().output());
        assertTrue(result.orElseThrow().supportsFrameworkTestAnnotations());
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
    void quarkusTestAnnotationCanPassThroughWhenDescriptorSupportsIt() throws java.io.IOException {
        java.nio.file.Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        java.nio.file.Files.createDirectories(testClass.getParent());
        java.nio.file.Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");
        QuarkusFrameworkTestRunner runner = runner(true, "Worker tests successful\n");

        Optional<FrameworkTestRunResult> result = runner.runIfEnabled(request(quarkusConfig()));

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().supportsFrameworkTestAnnotations());
        assertEquals("Worker tests successful\n", result.orElseThrow().output());
    }
}
