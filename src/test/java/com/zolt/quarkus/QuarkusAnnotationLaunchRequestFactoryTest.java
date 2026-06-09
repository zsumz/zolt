package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationLaunchRequestFactoryTest {
    private final QuarkusAnnotationLaunchRequestFactory factory = new QuarkusAnnotationLaunchRequestFactory(":");

    @Test
    void createsDeterministicAnnotationLaunchRequest() {
        QuarkusAnnotationLaunchRequest request = factory.create(
                plan(List.of(
                        unsupported("com/example/BetaTest.class", "@QuarkusIntegrationTest"),
                        unsupported("com/example/AlphaTest.class", "@QuarkusTest"),
                        unsupported("com/example/AlphaTest.class", "@QuarkusTest"))),
                api());

        assertEquals(List.of("com.example.AlphaTest", "com.example.BetaTest"), request.testClasses());
        assertEquals(List.of(
                "-Duser.dir=/repo",
                "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"),
                request.jvmArguments());
        assertEquals(List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")),
                request.launcherClasspath());
        assertEquals(List.of(
                "org.junit.platform.console.ConsoleLauncher",
                "execute",
                "--disable-banner",
                "--class-path",
                "/repo/target/test-classes:/repo/target/classes:/cache/junit-platform-console.jar",
                "--select-class",
                "com.example.AlphaTest",
                "--select-class",
                "com.example.BetaTest",
                "--details",
                "summary"),
                request.consoleArguments());
        assertEquals(api(), request.api());
    }

    @Test
    void omitsJbossLogManagerPropertyWhenAbsent() {
        QuarkusAnnotationLaunchRequest request = factory.create(
                new QuarkusTestWorkerPlan(
                        descriptor(false),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of(unsupported("com/example/HttpTest.class", "@QuarkusTest"))),
                api());

        assertEquals(List.of(
                "-Duser.dir=/repo",
                "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat"),
                request.jvmArguments());
    }

    @Test
    void requiresQuarkusSpecificTestClasses() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(
                        new QuarkusTestWorkerPlan(
                                descriptor(true),
                                QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                                List.of()),
                        api()));

        assertTrue(exception.getMessage().contains("at least one Quarkus-specific test class"));
    }

    @Test
    void rejectsNonClassEntries() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(
                        plan(List.of(unsupported("com/example/HttpTest.txt", "@QuarkusTest"))),
                        api()));

        assertTrue(exception.getMessage().contains("expected a compiled .class file"));
        assertTrue(exception.getMessage().contains("com/example/HttpTest.txt"));
    }

    private static QuarkusTestWorkerPlan plan(List<QuarkusUnsupportedTest> unsupportedTests) {
        return new QuarkusTestWorkerPlan(
                descriptor(true),
                QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                unsupportedTests);
    }

    private static QuarkusUnsupportedTest unsupported(String relativePath, String annotation) {
        return new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes").resolve(relativePath),
                Path.of(relativePath),
                annotation);
    }

    private static QuarkusAnnotationApi api() {
        return new QuarkusAnnotationApi(
                "io.quarkus.test.junit.QuarkusTestExtension",
                "io.quarkus.test.junit.QuarkusTestProfile",
                "io.quarkus.test.junit.launcher.CustomLauncherInterceptor",
                List.of("io.quarkus.test.junit.launcher.JarLauncherProvider"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean jbossLogManagerPresent) {
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
                jbossLogManagerPresent,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }
}
