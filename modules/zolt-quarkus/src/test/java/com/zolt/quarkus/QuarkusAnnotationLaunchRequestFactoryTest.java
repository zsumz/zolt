package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationLaunchRequestFactoryTest {
    private final QuarkusAnnotationLaunchRequestFactory factory = new QuarkusAnnotationLaunchRequestFactory();

    @Test
    void createsDeterministicAnnotationLaunchRequest() {
        QuarkusAnnotationLaunchRequest request = factory.create(
                plan(List.of(
                        unsupported("com/example/BetaTest.class", "@QuarkusIntegrationTest"),
                        supported("com/example/AlphaTest.class", "@QuarkusTest"),
                        supported("com/example/AlphaTest.class", "@QuarkusTest"))),
                api());

        assertEquals(List.of("com.example.AlphaTest"), request.testClasses());
        assertEquals(List.of(
                "-Duser.dir=/repo",
                "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                "-Dzolt.quarkus.main-output-dir=/repo/target/classes",
                "-Dzolt.quarkus.test-output-dir=/repo/target/test-classes",
                "-Dzolt.quarkus.test-class-bean-diagnostic-file=/repo/target/quarkus/annotation-runner/test-class-bean-customizer.txt",
                "-Dquarkus.builder.graph-output=/repo/target/quarkus/annotation-runner/build-chain.dot",
                "-Dquarkus.arc.unremovable-types=com.example.AlphaTest",
                "-Djava.util.logging.manager=org.jboss.logmanager.LogManager"),
                request.jvmArguments());
        assertEquals(List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")),
                request.launcherClasspath());
        assertEquals(List.of(
                QuarkusAnnotationProgrammaticRunner.MAIN_CLASS,
                "com.example.AlphaTest"),
                request.consoleArguments());
        assertEquals(api(), request.api());
    }

    @Test
    void omitsJbossLogManagerPropertyWhenAbsent() {
        QuarkusAnnotationLaunchRequest request = factory.create(
                new QuarkusTestWorkerPlan(
                        descriptor(false),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of(supported("com/example/HttpTest.class", "@QuarkusTest"))),
                api());

        assertEquals(List.of(
                "-Duser.dir=/repo",
                "-Dquarkus-internal-test.serialized-app-model.path=/repo/target/quarkus/test-application-model.dat",
                "-Dzolt.quarkus.main-output-dir=/repo/target/classes",
                "-Dzolt.quarkus.test-output-dir=/repo/target/test-classes",
                "-Dzolt.quarkus.test-class-bean-diagnostic-file=/repo/target/quarkus/annotation-runner/test-class-bean-customizer.txt",
                "-Dquarkus.builder.graph-output=/repo/target/quarkus/annotation-runner/build-chain.dot",
                "-Dquarkus.arc.unremovable-types=com.example.HttpTest"),
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
                        plan(List.of(supported("com/example/HttpTest.txt", "@QuarkusTest"))),
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
        return annotation(relativePath, annotation, false);
    }

    private static QuarkusUnsupportedTest supported(String relativePath, String annotation) {
        return annotation(relativePath, annotation, true);
    }

    private static QuarkusUnsupportedTest annotation(String relativePath, String annotation, boolean supported) {
        return new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes").resolve(relativePath),
                Path.of(relativePath),
                annotation,
                supported);
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
