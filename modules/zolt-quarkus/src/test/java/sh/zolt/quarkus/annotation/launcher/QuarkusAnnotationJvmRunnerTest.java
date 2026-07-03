package sh.zolt.quarkus.annotation.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.annotation.QuarkusAnnotationApi;
import sh.zolt.quarkus.annotation.QuarkusAnnotationProgrammaticRunner;
import sh.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import sh.zolt.quarkus.testworker.QuarkusTestRunnerRequest;
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
                        "-Dzolt.quarkus.test-output-dir=/repo/target/test-classes",
                        "-Djava.util.logging.manager=org.jboss.logmanager.LogManager",
                        "-classpath",
                        "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                        QuarkusAnnotationProgrammaticRunner.MAIN_CLASS,
                        "com.example.HttpTest"),
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
    void usesLauncherClasspathSeparatelyFromConsoleClasspath() {
        QuarkusAnnotationLaunchRequest request = new QuarkusAnnotationLaunchRequest(
                descriptor(),
                api(),
                List.of("com.example.HttpTest"),
                List.of("-Duser.dir=/repo"),
                List.of(
                        Path.of("/launcher/junit-platform-console.jar"),
                        Path.of("/launcher/quarkus-junit.jar")),
                List.of(
                        QuarkusAnnotationProgrammaticRunner.MAIN_CLASS,
                        "com.example.HttpTest"));
        QuarkusAnnotationJvmRunner runner = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                () -> "/zolt/zolt.jar:/zolt/picocli.jar",
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));

        assertEquals(List.of(
                        "/jdk/bin/java",
                        "-Duser.dir=/repo",
                        "-classpath",
                        "/zolt/zolt.jar:/zolt/picocli.jar:/launcher/junit-platform-console.jar:/launcher/quarkus-junit.jar",
                        QuarkusAnnotationProgrammaticRunner.MAIN_CLASS,
                        "com.example.HttpTest"),
                runner.command(request));
    }

    @Test
    void ignoresBlankAndNullWorkerClasspathEntries() {
        QuarkusAnnotationJvmRunner blankWorkerClasspath = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                () -> "/worker/../worker/zolt.jar::",
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));
        QuarkusAnnotationJvmRunner nullWorkerClasspath = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                () -> null,
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));

        assertEquals(
                "/worker/zolt.jar:/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                blankWorkerClasspath.command(request()).get(6));
        assertEquals(
                "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                nullWorkerClasspath.command(request()).get(6));
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

    @Test
    void requiresLaunchRequestWhenBuildingCommand() {
        QuarkusAnnotationJvmRunner runner = new QuarkusAnnotationJvmRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusAnnotationJvmRunner.Result(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> runner.command(null));

        assertTrue(exception.getMessage().contains("launch request is required"));
    }

    @Test
    void rejectsInvalidConstructorInputs() {
        QuarkusAugmentationException separator = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusAnnotationJvmRunner(
                        " ",
                        Path.of("/jdk/bin/java"),
                        command -> new QuarkusAnnotationJvmRunner.Result(0, "")));
        assertTrue(separator.getMessage().contains("path separator is required"));

        QuarkusAugmentationException javaExecutable = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusAnnotationJvmRunner(
                        ":",
                        null,
                        command -> new QuarkusAnnotationJvmRunner.Result(0, "")));
        assertTrue(javaExecutable.getMessage().contains("Java executable is required"));

        QuarkusAugmentationException workerClasspath = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusAnnotationJvmRunner(
                        ":",
                        Path.of("/jdk/bin/java"),
                        null,
                        command -> new QuarkusAnnotationJvmRunner.Result(0, "")));
        assertTrue(workerClasspath.getMessage().contains("worker classpath is required"));

        QuarkusAugmentationException processRunner = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusAnnotationJvmRunner(
                        ":",
                        Path.of("/jdk/bin/java"),
                        () -> "",
                        null));
        assertTrue(processRunner.getMessage().contains("process runner is required"));
    }

    private static QuarkusAnnotationLaunchRequest request() {
        return new QuarkusAnnotationLaunchRequest(
                descriptor(),
                api(),
                List.of("com.example.HttpTest"),
                List.of(
                        "-Duser.dir=/repo",
                        "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                        "-Dzolt.quarkus.test-output-dir=/repo/target/test-classes",
                        "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"),
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")),
                List.of(
                        QuarkusAnnotationProgrammaticRunner.MAIN_CLASS,
                        "com.example.HttpTest"));
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
