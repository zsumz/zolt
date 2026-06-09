package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationJvmRunnerTest {
    @Test
    void buildsCommandFromAnnotationLaunchRequest() {
        QuarkusAnnotationJvmRunner runner = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));

        assertEquals(List.of(
                        "/jdk/bin/java",
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
                        "--select-class",
                        "com.example.HttpTest",
                        "--details",
                        "summary"),
                runner.command(request()));
    }

    @Test
    void returnsProcessResult() {
        QuarkusAnnotationJvmRunner runner = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusAnnotationJvmRunner.Result(5, "tests failed\n"));

        QuarkusAnnotationJvmRunner.Result result = runner.run(request());

        assertEquals(5, result.exitCode());
        assertEquals("tests failed\n", result.output());
    }

    @Test
    void requiresLaunchRequest() {
        QuarkusAnnotationJvmRunner runner = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> runner.run(null));

        assertTrue(exception.getMessage().contains("launch request is required"));
    }

    private static QuarkusAnnotationLaunchRequest request() {
        return new QuarkusAnnotationLaunchRequest(
                descriptor(),
                api(),
                List.of("com.example.HttpTest"),
                List.of(
                        "-Duser.dir=/repo",
                        "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                        "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"),
                List.of(
                        "org.junit.platform.console.ConsoleLauncher",
                        "execute",
                        "--disable-banner",
                        "--class-path",
                        "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                        "--select-class",
                        "com.example.HttpTest",
                        "--details",
                        "summary"));
    }

    private static QuarkusAnnotationApi api() {
        return new QuarkusAnnotationApi(
                "io.quarkus.test.junit.QuarkusTestExtension",
                "io.quarkus.test.junit.QuarkusTestProfile",
                "io.quarkus.test.junit.launcher.CustomLauncherInterceptor",
                List.of("io.quarkus.test.junit.launcher.JarLauncherProvider"));
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
                true,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }
}
