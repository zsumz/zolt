package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.TestJvmArguments;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusPlainJunitWorkerRunnerTest {
    @Test
    void buildsJUnitConsoleCommandFromDescriptorClasspath() {
        QuarkusTestRunnerDescriptor descriptor = descriptor(
                true,
                new TestJvmArguments(List.of("-Dlibrary.mode=true")));
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(0, ""));

        assertEquals(List.of(
                        "/jdk/bin/java",
                        "-Dlibrary.mode=true",
                        "-Duser.dir=/repo",
                        "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                        "-Djava.util.logging.manager=org.jboss.logmanager.LogManager",
                        "-classpath",
                        "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                        "org.junit.platform.console.ConsoleLauncher",
                        "execute",
                        "--disable-banner",
                        "--class-path",
                        "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                        "--scan-class-path=/repo/target/test-classes",
                        "--include-classname",
                        "^(Test.*|.+[.$]Test.*|.*Tests?)$",
                        "--include-classname",
                        ".*Spec",
                        "--details",
                        "summary"),
                runner.command(descriptor));
    }

    @Test
    void omitsJbossLogManagerPropertyWhenNotPresent() {
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(0, ""));

        List<String> command = runner.command(descriptor(false));

        assertTrue(command.stream().noneMatch(value -> value.contains("java.util.logging.manager")));
    }

    @Test
    void returnsProcessResult() {
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(7, "tests failed\n"));

        QuarkusPlainJunitWorkerRunner.Result result = runner.run(descriptor(true));

        assertEquals(7, result.exitCode());
        assertEquals("tests failed\n", result.output());
    }

    @Test
    void requiresDescriptor() {
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> runner.run(null));

        assertTrue(exception.getMessage().contains("descriptor is required"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean jbossLogManagerPresent) {
        return descriptor(jbossLogManagerPresent, TestJvmArguments.empty());
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean jbossLogManagerPresent, TestJvmArguments jvmArguments) {
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
                jbossLogManagerPresent,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")),
                com.zolt.build.TestSelection.empty(),
                jvmArguments);
    }
}
