package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerTest {
    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = worker(descriptor(), new ByteArrayOutputStream(), err);

        int exitCode = worker.run(new String[] {});

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires a test runner descriptor path"));
    }

    @Test
    void validatesDescriptorThenFailsHonestlyUntilDedicatedRunnerExists() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusUnsupportedTest unsupportedTest = new QuarkusUnsupportedTest(
                descriptor.testOutputDirectory().resolve("com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest");
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS,
                        List.of(unsupportedTest)),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(2, exitCode);
        assertTrue(output(out).contains("Runner mode: plain-junit"));
        assertTrue(output(out).contains("Status: blocked by unsupported Quarkus test annotations"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 1"));
        assertTrue(output(out).contains("com/example/HttpTest.class (@QuarkusTest)"));
        assertTrue(output(err).contains("Quarkus-specific test annotations are not supported"));
        assertTrue(output(err).contains("launcher/session listeners"));
    }

    @Test
    void runsPlainJunitReadyPlan() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, "Tests passed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(0, exitCode);
        assertTrue(output(out).contains("Status: plain JUnit ready"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 0"));
        assertTrue(output(out).contains("Tests passed"));
        assertEquals("", output(err));
    }

    @Test
    void routesSelectedQuarkusAnnotationPlanToAnnotationRunner() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor(true);
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of(new QuarkusUnsupportedTest(
                                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                                Path.of("com/example/HttpTest.class"),
                                "@QuarkusTest"))),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, "plain runner should not run\n"),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, "Quarkus annotation tests passed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(0, exitCode);
        assertTrue(output(out).contains("Status: Quarkus annotation runner selected"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 1"));
        assertTrue(output(out).contains("com/example/HttpTest.class (@QuarkusTest)"));
        assertTrue(output(out).contains("Quarkus annotation tests passed"));
        assertEquals("", output(err));
    }

    @Test
    void annotationRunnerExecutesGeneratedLaunchRequest() {
        List<QuarkusAnnotationLaunchRequest> launchRequests = new java.util.ArrayList<>();
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(
                                        Path.of("/cache/io/quarkus/quarkus-core-3.33.2.jar"),
                                        Path.of("/cache/io/quarkus/quarkus-rest-3.33.2.jar"),
                                        Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> {
                            launchRequests.add(request);
                            return new QuarkusAnnotationJvmRunner.Result(0, "Quarkus annotation tests passed\n");
                        })
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(0, result.exitCode());
        assertEquals("Quarkus annotation tests passed\n", result.output());
        assertEquals(List.of("com.example.HttpTest"), launchRequests.getFirst().testClasses());
    }

    @Test
    void annotationRunnerExplainsBuildChainBuilderClassloaderSplit() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(1, """
                                java.lang.ClassCastException: class io.quarkus.builder.BuildChainBuilder cannot be cast to class io.quarkus.builder.BuildChainBuilder
                                    at io.quarkus.test.junit.TestBuildChainFunction$1.accept(TestBuildChainFunction.java:51)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Quarkus annotation test bootstrap hit a Quarkus classloader split"));
        assertTrue(result.output().contains("dedicated @QuarkusTest runner path"));
        assertTrue(result.output().contains("deployment/runtime classloader ownership work"));
        assertTrue(result.output().contains("BuildChainBuilder cannot be cast"));
    }

    @Test
    void annotationRunnerExplainsMissingBuilderApiOnLauncherClasspath() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(1, """
                                Cause: java.lang.NoClassDefFoundError: io/quarkus/builder/item/MultiBuildItem
                                    at io.quarkus.test.junit.TestBuildChainFunction.collectTestAnnotationItems(TestBuildChainFunction.java:185)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("could not load the Quarkus builder API"));
        assertTrue(result.output().contains("builder API types"));
        assertTrue(result.output().contains("quarkus-builder is absent from the annotation JVM launcher classpath"));
        assertTrue(result.output().contains("MultiBuildItem"));
    }

    @Test
    void annotationRunnerExplainsTestConfigClassloaderMismatch() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(1, """
                                java.lang.IllegalArgumentException: argument type mismatch
                                    at io.quarkus.test.junit.classloading.FacadeClassLoader.initialiseTestConfig(FacadeClassLoader.java:625)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Quarkus test configuration initialization"));
        assertTrue(result.output().contains("classloader type mismatch"));
        assertTrue(result.output().contains("moved this descriptor-enabled probe past the BuildChainBuilder split"));
        assertTrue(result.output().contains("argument type mismatch"));
    }

    @Test
    void annotationRunnerExplainsTestConfigLauncherSessionMismatch() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(255, """
                                java.lang.ClassCastException: class io.quarkus.test.junit.classloading.QuarkusTestConfigProviderResolver cannot be cast to class io.quarkus.test.config.TestConfigProviderResolver
                                    at io.quarkus.test.config.ConfigLauncherSession.launcherSessionClosed(ConfigLauncherSession.java:38)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(255, result.exitCode());
        assertTrue(result.output().contains("JUnit launcher-session cleanup"));
        assertTrue(result.output().contains("test config resolver ownership mismatch"));
        assertTrue(result.output().contains("moved this descriptor-enabled probe past FacadeClassLoader"));
        assertTrue(result.output().contains("QuarkusTestConfigProviderResolver cannot be cast"));
    }

    @Test
    void annotationRunnerExplainsMissingTestConfigMapping() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(1, """
                                org.junit.jupiter.engine.execution.ConditionEvaluationException: Failed to evaluate condition [io.quarkus.test.junit.QuarkusTestExtension]
                                Caused by: java.util.NoSuchElementException: SRCFG00027: Could not find a mapping for io.quarkus.deployment.dev.testing.TestConfig
                                    at io.quarkus.test.junit.AbstractJvmQuarkusTestExtension.evaluateExecutionCondition(AbstractJvmQuarkusTestExtension.java:164)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached Quarkus JUnit execution"));
        assertTrue(result.output().contains("filtering the conflicting JUnit launcher-session listener"));
        assertTrue(result.output().contains("missing Quarkus TestConfig mapping"));
        assertTrue(result.output().contains("SRCFG00027"));
    }

    @Test
    void annotationRunnerExplainsTestHttpEndpointProviderSplit() {
        QuarkusAnnotationWorkerRunner.Result result = new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(1, """
                                java.lang.RuntimeException: java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider: io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider not a subtype
                                    at io.quarkus.test.junit.QuarkusTestExtension.throwBootFailureException(QuarkusTestExtension.java:672)
                                Caused by: java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider: io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider not a subtype
                                    at io.quarkus.runtime.test.TestHttpEndpointProvider.load(TestHttpEndpointProvider.java:17)
                                """))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached Quarkus application startup"));
        assertTrue(result.output().contains("runtime service-provider classloader split"));
        assertTrue(result.output().contains("moved past the TestConfig mapping blocker"));
        assertTrue(result.output().contains("Classpath ownership:"));
        assertTrue(result.output().contains("TestHttpEndpointProvider service loading"));
        assertTrue(result.output().contains("programmatic JUnit launcher"));
        assertTrue(result.output().contains("ResteasyReactiveTestHttpProvider not a subtype"));
    }

    @Test
    void returnsPlainJunitFailureCodeAndOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(3, "Tests failed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(3, exitCode);
        assertTrue(output(out).contains("Tests failed"));
        assertEquals("", output(err));
    }

    @Test
    void reportsDescriptorReadFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = new QuarkusTestWorker(
                path -> {
                    throw new QuarkusAugmentationException("bad descriptor");
                },
                descriptor -> new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptor -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {"/repo/target/quarkus/zolt-test-bootstrap.properties"});

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: bad descriptor"));
    }

    @Test
    void reportsPlanFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = new QuarkusTestWorker(
                path -> descriptor(),
                descriptor -> {
                    throw new QuarkusAugmentationException("bad plan");
                },
                descriptor -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {"/repo/target/quarkus/zolt-test-bootstrap.properties"});

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: bad plan"));
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                plan,
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                planToRun -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            QuarkusTestWorker.PlainJunitRunner plainJunitRunner,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                plan,
                plainJunitRunner,
                planToRun -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            QuarkusTestWorker.PlainJunitRunner plainJunitRunner,
            QuarkusTestWorker.QuarkusAnnotationRunner quarkusAnnotationRunner,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return new QuarkusTestWorker(
                path -> descriptor,
                actualDescriptor -> plan,
                plainJunitRunner,
                quarkusAnnotationRunner,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return descriptor(QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }

    private static QuarkusAnnotationApi api() {
        return new QuarkusAnnotationApi(
                "io.quarkus.test.junit.QuarkusTestExtension",
                "io.quarkus.test.junit.QuarkusTestProfile",
                "io.quarkus.test.junit.launcher.CustomLauncherInterceptor",
                List.of("io.quarkus.test.junit.launcher.JarLauncherProvider"));
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
