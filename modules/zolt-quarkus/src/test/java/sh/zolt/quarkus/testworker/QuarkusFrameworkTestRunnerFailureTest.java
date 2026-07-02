package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.runtime.TestRunException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class QuarkusFrameworkTestRunnerFailureTest extends QuarkusFrameworkTestRunnerTestSupport {
    @Test
    void missingSerializedModelProducesActionableError() {
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> java.util.Optional.empty(),
                request -> {
                    throw new AssertionError("Descriptor should not be written without a serialized model.");
                },
                () -> java.util.List.of(Path.of("/zolt/zolt.jar")),
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
        QuarkusFrameworkTestRunner runner = new QuarkusFrameworkTestRunner(
                (projectDirectory, config) -> java.util.Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, false),
                () -> java.util.List.of(Path.of("/zolt/zolt.jar")),
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
                (projectDirectory, config) -> java.util.Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, true),
                () -> java.util.List.of(Path.of("/zolt/zolt.jar")),
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
