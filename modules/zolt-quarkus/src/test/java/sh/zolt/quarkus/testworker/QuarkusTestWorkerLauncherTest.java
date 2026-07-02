package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerLauncherTest {
    @Test
    void buildsIsolatedWorkerCommandWithTestRuntimeClasspath() {
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusTestWorkerLauncher launcher = new QuarkusTestWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestWorkerLauncher.ProcessResult(0, ""));

        assertEquals(List.of(
                "/jdk/bin/java",
                "-D" + QuarkusTestWorkerLauncher.WORKER_CLASSPATH_PROPERTY + "=/zolt/zolt.jar",
                "-classpath",
                "/zolt/zolt.jar:/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                QuarkusTestWorker.MAIN_CLASS,
                "/repo/target/quarkus/zolt-test-bootstrap.properties"), launcher.command(descriptor));
    }

    @Test
    void returnsWorkerOutputWhenProcessSucceeds() {
        QuarkusTestWorkerLauncher launcher = new QuarkusTestWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestWorkerLauncher.ProcessResult(0, "tests passed\n"));

        assertEquals("tests passed\n", launcher.run(descriptor()));
    }

    @Test
    void failsWhenWorkerProcessFails() {
        QuarkusTestWorkerLauncher launcher = new QuarkusTestWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestWorkerLauncher.ProcessResult(2, "not implemented\n"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.run(descriptor()));

        assertTrue(exception.getMessage().contains("Quarkus test worker failed with exit code 2"));
        assertTrue(exception.getMessage().contains("unsupported Quarkus test shapes"));
        assertTrue(exception.getMessage().contains("not implemented"));
    }

    @Test
    void requiresWorkerClasspath() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusTestWorkerLauncher(
                        ":",
                        Path.of("/jdk/bin/java"),
                        List.of(),
                        command -> new QuarkusTestWorkerLauncher.ProcessResult(0, "")));

        assertTrue(exception.getMessage().contains("worker classpath is required"));
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }
}
