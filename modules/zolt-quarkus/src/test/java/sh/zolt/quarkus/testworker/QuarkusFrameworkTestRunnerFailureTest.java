package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.QuarkusPlanException;
import sh.zolt.test.runtime.TestRunException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusFrameworkTestRunnerFailureTest extends QuarkusFrameworkTestRunnerTestSupport {
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
    void applicationModelWriteFailureKeepsRecoveryHint() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> {
                    throw new QuarkusAugmentationException("serialized model cache is stale");
                },
                request -> {
                    throw new AssertionError("Descriptor should not be written when application model write fails.");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("Could not prepare Quarkus test application model"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve`, then run `zolt test` again"));
        assertTrue(exception.getMessage().contains("serialized model cache is stale"));
    }

    @Test
    void descriptorWriteFailureKeepsRecoveryHint() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> {
                    throw new QuarkusAugmentationException("descriptor output is read-only");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("Could not write Quarkus test runner descriptor"));
        assertTrue(exception.getMessage().contains("Clean the configured Quarkus output directory"));
        assertTrue(exception.getMessage().contains("descriptor output is read-only"));
    }

    @Test
    void workerFailurePropagatesWorkerMessage() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, true),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("worker process exited with 17");
                });

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertEquals("worker process exited with 17", exception.getMessage());
    }

    @Test
    void testPlanFailurePropagatesPlanMessage() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, true),
                (projectDirectory, config) -> {
                    throw new QuarkusPlanException("could not scan target/test-classes");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertEquals("could not scan target/test-classes", exception.getMessage());
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
    void hiddenBootstrapScannerIgnoresBlankAndPlainJUnitOutput() {
        assertDoesNotThrow(() -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure(null));
        assertDoesNotThrow(() -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure("  \n"));
        assertDoesNotThrow(() -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure("""
                org.opentest4j.AssertionFailedError: expected true
                    at com.example.FastTest.runs(FastTest.java:12)
                Tests failed
                """));
    }

    @Test
    void hiddenBootstrapScannerDetectsBootstrapExceptionAndNullPointerException() {
        TestRunException bootstrap = assertThrows(
                TestRunException.class,
                () -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure("""
                        io.quarkus.bootstrap.BootstrapException: unable to bootstrap
                            at io.quarkus.test.junit.QuarkusTestExtension.beforeAll(QuarkusTestExtension.java:10)
                        Tests passed
                        """));
        assertTrue(bootstrap.getMessage().contains("Quarkus test bootstrap failed"));

        TestRunException nullPointer = assertThrows(
                TestRunException.class,
                () -> QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure("""
                        java.lang.NullPointerException: Cannot invoke "Object.toString()"
                            at io.quarkus.test.junit.QuarkusTestExtension.beforeAll(QuarkusTestExtension.java:10)
                        Tests passed
                        """));
        assertTrue(nullPointer.getMessage().contains("Quarkus test bootstrap failed"));
    }

    @Test
    void quarkusTestAnnotationFailsBeforeWorkerRuns() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, false),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> "unused");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("`@QuarkusTest` execution is not supported"));
        assertTrue(exception.getMessage().contains("dedicated Quarkus test runner"));
        assertTrue(exception.getMessage().contains("com/example/QuarkusHttpTest.class"));
    }

    @Test
    void unsupportedQuarkusTestProfileFailsBeforeWorkerRunsEvenWhenDescriptorsSupportAnnotations() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/ProfiledHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, """
                constant-pool:Lio/quarkus/test/junit/QuarkusTest;
                constant-pool:Lio/quarkus/test/junit/TestProfile;
                """);
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, true),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new AssertionError("Worker should not run for unsupported Quarkus test modes.");
                });

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> runner.runIfEnabled(request(quarkusConfig())));

        assertTrue(exception.getMessage().contains("`@TestProfile` execution is not supported"));
        assertTrue(exception.getMessage().contains("broader Quarkus test modes"));
        assertTrue(exception.getMessage().contains("com/example/ProfiledHttpTest.class"));
    }
}
