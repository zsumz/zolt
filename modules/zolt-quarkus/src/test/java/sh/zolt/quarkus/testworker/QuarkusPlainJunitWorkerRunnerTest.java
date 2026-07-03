package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    void buildsSelectionArgumentsWithoutClasspathScanWhenSelectorsAreExplicit() {
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.FastTest"),
                List.of(new TestSelection.MethodSelector("com.example.OtherTest", "runs")),
                List.of("Fast*"),
                List.of("smoke"),
                List.of("slow"));
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(0, ""));

        List<String> command = runner.command(descriptor(true, TestJvmArguments.empty(), selection));

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
                        "com.example.FastTest",
                        "--select-method",
                        "com.example.OtherTest#runs",
                        "--include-classname",
                        ".*Fast.*",
                        "--include-tag",
                        "smoke",
                        "--exclude-tag",
                        "slow",
                        "--details",
                        "summary"),
                command);
        assertFalse(command.stream().anyMatch(value -> value.startsWith("--scan-class-path")));
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
        List<Map<String, String>> environments = new ArrayList<>();
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                new QuarkusPlainJunitWorkerRunner.ProcessRunner() {
                    @Override
                    public QuarkusPlainJunitWorkerRunner.Result run(List<String> command) {
                        throw new AssertionError("Environment-aware Quarkus plain JUnit runner should be used.");
                    }

                    @Override
                    public QuarkusPlainJunitWorkerRunner.Result run(
                            List<String> command,
                            Map<String, String> environment) {
                        environments.add(environment);
                        return new QuarkusPlainJunitWorkerRunner.Result(7, "tests failed\n");
                    }
                });

        QuarkusPlainJunitWorkerRunner.Result result = runner.run(new QuarkusTestRunnerDescriptor(
                descriptor(true).descriptorFile(),
                descriptor(true).testRuntimeClasspathFile(),
                descriptor(true).projectDirectory(),
                descriptor(true).mainOutputDirectory(),
                descriptor(true).testOutputDirectory(),
                descriptor(true).serializedApplicationModel(),
                descriptor(true).bootstrapDescriptorFile(),
                descriptor(true).runnerMode(),
                descriptor(true).supportsQuarkusTestAnnotations(),
                descriptor(true).jbossLogManagerPresent(),
                descriptor(true).testRuntimeClasspath(),
                descriptor(true).testSelection(),
                descriptor(true).jvmArguments(),
                Map.of("TZ", "America/Chicago")));

        assertEquals(7, result.exitCode());
        assertEquals("tests failed\n", result.output());
        assertEquals(Map.of("TZ", "America/Chicago"), environments.getFirst());
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

    @Test
    void commandRequiresDescriptor() {
        QuarkusPlainJunitWorkerRunner runner = new QuarkusPlainJunitWorkerRunner(
                ":",
                Path.of("/jdk/bin/java"),
                command -> new QuarkusPlainJunitWorkerRunner.Result(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> runner.command(null));

        assertTrue(exception.getMessage().contains("descriptor is required"));
    }

    @Test
    void validatesRunnerConstructionInputs() {
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusPlainJunitWorkerRunner(
                                "",
                                Path.of("/jdk/bin/java"),
                                command -> new QuarkusPlainJunitWorkerRunner.Result(0, "")))
                .getMessage()
                .contains("path separator is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusPlainJunitWorkerRunner(
                                ":",
                                null,
                                command -> new QuarkusPlainJunitWorkerRunner.Result(0, "")))
                .getMessage()
                .contains("Java executable is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusPlainJunitWorkerRunner(
                                ":",
                                Path.of("/jdk/bin/java"),
                                null))
                .getMessage()
                .contains("process runner is required"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean jbossLogManagerPresent) {
        return descriptor(jbossLogManagerPresent, TestJvmArguments.empty());
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean jbossLogManagerPresent, TestJvmArguments jvmArguments) {
        return descriptor(jbossLogManagerPresent, jvmArguments, sh.zolt.test.TestSelection.empty());
    }

    private static QuarkusTestRunnerDescriptor descriptor(
            boolean jbossLogManagerPresent,
            TestJvmArguments jvmArguments,
            TestSelection testSelection) {
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
                testSelection,
                jvmArguments);
    }
}
