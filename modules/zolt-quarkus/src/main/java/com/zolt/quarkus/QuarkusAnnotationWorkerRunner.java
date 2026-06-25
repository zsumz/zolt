package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;
    private final LaunchRequestFactory launchRequestFactory;
    private final LaunchRunner launchRunner;
    private final QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic;
    private final TestIndexWriter testIndexWriter;

    public QuarkusAnnotationWorkerRunner() {
        this(
                new QuarkusAnnotationApiProbe()::probe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                new ReflectiveTestIndexWriter());
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        this(
                apiProbe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory) {
        this(apiProbe, launchRequestFactory, new QuarkusAnnotationJvmRunner()::run);
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner) {
        this(
                apiProbe,
                launchRequestFactory,
                launchRunner,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic) {
        this(
                apiProbe,
                launchRequestFactory,
                launchRunner,
                classpathSplitDiagnostic,
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic,
            TestIndexWriter testIndexWriter) {
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker API probe is required.");
        }
        if (launchRequestFactory == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch request factory is required.");
        }
        if (launchRunner == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch runner is required.");
        }
        if (classpathSplitDiagnostic == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation worker classpath split diagnostic is required.");
        }
        if (testIndexWriter == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation worker test index writer is required.");
        }
        this.apiProbe = apiProbe;
        this.launchRequestFactory = launchRequestFactory;
        this.launchRunner = launchRunner;
        this.classpathSplitDiagnostic = classpathSplitDiagnostic;
        this.testIndexWriter = testIndexWriter;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        QuarkusAnnotationLaunchRequest launchRequest = launchRequestFactory.create(plan, api);
        testIndexWriter.write(launchRequest);
        QuarkusAnnotationJvmRunner.Result result = launchRunner.run(launchRequest);
        return new Result(result.exitCode(), diagnosedOutput(launchRequest, result));
    }

    private String diagnosedOutput(QuarkusAnnotationLaunchRequest request, QuarkusAnnotationJvmRunner.Result result) {
        if (result.exitCode() == 0 || result.output() == null || result.output().isBlank()) {
            return result.output();
        }
        if (!buildChainBuilderClassloaderSplit(result.output())) {
            if (missingBuilderApi(result.output())) {
                return "error: Quarkus annotation test bootstrap could not load the Quarkus builder API from "
                        + "the annotation runner side. Zolt reached the dedicated @QuarkusTest runner path, "
                        + "but this fixture still needs a classloader arrangement where Quarkus JUnit can see "
                        + "builder API types without loading deployment-owned builder classes from the wrong side. "
                        + classpathSplitDiagnostic.describeMissingBuilderApi(request)
                        + "\n"
                        + result.output().stripTrailing()
                        + "\n";
            }
            if (testConfigClassloaderMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus test configuration initialization, "
                    + "then hit a classloader type mismatch inside FacadeClassLoader. The builder API ownership hint "
                    + "moved this descriptor-enabled probe past the BuildChainBuilder split, but Zolt still needs "
                    + "to align Quarkus test config class ownership before @QuarkusTest can be enabled. "
                    + "Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testConfigLauncherSessionMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached JUnit launcher-session cleanup, then hit "
                    + "a Quarkus test config resolver ownership mismatch. Zolt's test config parent-first hints "
                    + "moved this descriptor-enabled probe past FacadeClassLoader initialization, but the runner "
                    + "still needs to align QuarkusTestConfigProviderResolver and ConfigLauncherSession ownership "
                    + "before @QuarkusTest can be enabled. Keep using plain JUnit tests for now, or run "
                    + "`zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testHttpEndpointProviderSplit(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus application startup, then hit "
                    + "a runtime service-provider classloader split for TestHttpEndpointProvider. Running the "
                    + "annotation probe from the JVM classpath moved past the TestConfig mapping blocker, but "
                    + "Zolt still needs to align Quarkus runtime service loading before @QuarkusTest can be "
                    + "enabled. Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect "
                    + "blocked tests. "
                    + classpathSplitDiagnostic.describeRuntimeServiceProviderSplit(request)
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testScopeSetupProviderSplit(result.output())) {
                return "error: Quarkus annotation test bootstrap reached per-test scope setup, then hit "
                    + "a runtime service-provider classloader split for TestScopeSetup. Zolt can now produce "
                    + "Quarkus additional-bean build items for selected @QuarkusTest classes, but the runner "
                    + "still needs to align Arc test request-scope provider ownership before @QuarkusTest can "
                    + "be enabled. Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` "
                    + "to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (applicationClassMissingDuringHttpRequest(result.output())) {
                return "error: Quarkus annotation test bootstrap reached REST Assured HTTP execution, then "
                    + "the running Quarkus application could not load an application class. Zolt moved this "
                    + "descriptor-enabled probe past Arc test bean registration and Arc test request-scope "
                    + "service loading, but the runner still needs to align application class visibility in "
                    + "the Quarkus runtime classloader before @QuarkusTest can be enabled. Keep using plain "
                    + "JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (configProducerVerifierMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached config-backed injection, then hit "
                    + "a SmallRye Config producer verifier mismatch. Zolt's supported direct "
                    + "@QuarkusTest fixture keeps SmallRye Config provider types parent-first while leaving "
                    + "the SmallRye CDI config producer runtime-owned; this failure means those ownership "
                    + "hints regressed or the project is using an unproven Quarkus/SmallRye classloading "
                    + "shape. Run `zolt quarkus test-plan` to inspect the current annotation-runner boundary."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testConfigMappingMissing(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus JUnit execution through Zolt's "
                    + "programmatic runner and facade-loader context-classloader handoff, then hit a missing "
                    + "Quarkus TestConfig mapping. That handoff moved this descriptor-enabled probe past the "
                    + "runtime TestHttpEndpointProvider service-loading split, but Zolt still needs to align "
                    + "Quarkus test config mapping ownership before @QuarkusTest can be enabled. Keep using "
                    + "plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testClassBeanMissing(result.output())) {
                return "error: Quarkus annotation test bootstrap started the Quarkus application, then Arc could "
                    + "not instantiate the selected @QuarkusTest class as a CDI bean. Zolt moved this "
                    + "descriptor-enabled probe past runtime service loading and can prove the enriched "
                    + "test-class index contains the selected class as a Quarkus build-chain test bean candidate, "
                    + "but the actual Quarkus test augmentation path still does not register it as an Arc bean. "
                    + "Zolt still needs to align TestClassBeanBuildItem production with Arc additional-bean "
                    + "registration under the descriptor-owned test application model. Keep using plain JUnit tests for now, or run "
                    + "`zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            return result.output();
        }
        return "error: Quarkus annotation test bootstrap hit a Quarkus classloader split while loading "
                + "io.quarkus.builder.BuildChainBuilder. Zolt reached the dedicated @QuarkusTest runner path, "
                + "but this fixture still needs Quarkus deployment/runtime classloader ownership work before "
                + "Quarkus test annotations can be enabled. Keep using plain JUnit tests for now, or run "
                + "`zolt quarkus test-plan` to inspect blocked tests. "
                + classpathSplitDiagnostic.describe(request)
                + "\n"
                + result.output().stripTrailing()
                + "\n";
    }

    private static boolean buildChainBuilderClassloaderSplit(String output) {
        return output.contains("io.quarkus.test.junit")
                && output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                && output.contains("cannot be cast to class io.quarkus.builder.BuildChainBuilder");
    }

    private static boolean missingBuilderApi(String output) {
        return output.contains("io.quarkus.test.junit")
                && output.contains("NoClassDefFoundError: io/quarkus/builder/item/MultiBuildItem");
    }

    private static boolean testConfigClassloaderMismatch(String output) {
        return output.contains("io.quarkus.test.junit.classloading.FacadeClassLoader.initialiseTestConfig")
                && output.contains("java.lang.IllegalArgumentException: argument type mismatch");
    }

    private static boolean testConfigLauncherSessionMismatch(String output) {
        return output.contains("io.quarkus.test.config.ConfigLauncherSession.launcherSessionClosed")
                && output.contains("QuarkusTestConfigProviderResolver cannot be cast")
                && output.contains("io.quarkus.test.config.TestConfigProviderResolver");
    }

    private static boolean testConfigMappingMissing(String output) {
        return output.contains("SRCFG00027: Could not find a mapping for io.quarkus.deployment.dev.testing.TestConfig")
                && output.contains("io.quarkus.test.junit");
    }

    private static boolean testHttpEndpointProviderSplit(String output) {
        return output.contains("java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider")
                && output.contains("not a subtype");
    }

    private static boolean testScopeSetupProviderSplit(String output) {
        return output.contains("java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestScopeSetup")
                && output.contains("not a subtype");
    }

    private static boolean applicationClassMissingDuringHttpRequest(String output) {
        return output.contains("HTTP/1.1 500 Internal Server Error")
                && output.contains("java.lang.NoClassDefFoundError")
                && output.contains("quarkusrestinvoker");
    }

    private static boolean configProducerVerifierMismatch(String output) {
        return output.contains("java.lang.VerifyError")
                && output.contains("ConfigProducer_ClientProxy.produceStringConfigProperty")
                && output.contains("io/smallrye/config/inject/ConfigProducer")
                && output.contains("Bad access to protected data");
    }

    private static boolean testClassBeanMissing(String output) {
        return output.contains("jakarta.enterprise.inject.UnsatisfiedResolutionException")
                && output.contains("No bean found for required type")
                && output.contains("io.quarkus.test.junit");
    }

    public record Result(int exitCode, String output) {
    }

    @FunctionalInterface
    interface ApiProbe {
        QuarkusAnnotationApi probe(QuarkusTestRunnerDescriptor descriptor);
    }

    @FunctionalInterface
    interface LaunchRequestFactory {
        QuarkusAnnotationLaunchRequest create(QuarkusTestWorkerPlan plan, QuarkusAnnotationApi api);
    }

    @FunctionalInterface
    interface LaunchRunner {
        QuarkusAnnotationJvmRunner.Result run(QuarkusAnnotationLaunchRequest request);
    }

    @FunctionalInterface
    interface TestIndexWriter {
        void write(QuarkusAnnotationLaunchRequest request);
    }

    private static final class ReflectiveTestIndexWriter implements TestIndexWriter {
        @Override
        public void write(QuarkusAnnotationLaunchRequest request) {
            if (request.testClasses().isEmpty()) {
                return;
            }
            try {
                java.nio.file.Path outputDirectory = request.descriptor()
                        .testOutputDirectory()
                        .toAbsolutePath()
                        .normalize();
                new QuarkusTestIndexWriter().write(outputDirectory, request.testClasses());
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new QuarkusAugmentationException(
                        "Could not write Quarkus test class index before annotation execution. "
                                + "Check that quarkus-test-common and Jandex are on the test runtime classpath.",
                        exception);
            }
        }
    }
}
