package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;
    private final LaunchRequestFactory launchRequestFactory;
    private final LaunchRunner launchRunner;
    private final QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic;

    public QuarkusAnnotationWorkerRunner() {
        this(
                new QuarkusAnnotationApiProbe()::probe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic());
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        this(
                apiProbe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic());
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
        this(apiProbe, launchRequestFactory, launchRunner, new QuarkusAnnotationClasspathSplitDiagnostic());
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic) {
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
        this.apiProbe = apiProbe;
        this.launchRequestFactory = launchRequestFactory;
        this.launchRunner = launchRunner;
        this.classpathSplitDiagnostic = classpathSplitDiagnostic;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        QuarkusAnnotationLaunchRequest launchRequest = launchRequestFactory.create(plan, api);
        QuarkusAnnotationJvmRunner.Result result = launchRunner.run(launchRequest);
        return new Result(result.exitCode(), diagnosedOutput(launchRequest, result));
    }

    private String diagnosedOutput(QuarkusAnnotationLaunchRequest request, QuarkusAnnotationJvmRunner.Result result) {
        if (result.exitCode() == 0 || result.output() == null || result.output().isBlank()) {
            return result.output();
        }
        if (!buildChainBuilderClassloaderSplit(result.output())) {
            if (!missingBuilderApi(result.output())) {
                return result.output();
            }
            return "error: Quarkus annotation test bootstrap could not load the Quarkus builder API from "
                    + "the annotation runner side. Zolt reached the dedicated @QuarkusTest runner path, "
                    + "but this fixture still needs a classloader arrangement where Quarkus JUnit can see "
                    + "builder API types without loading deployment-owned builder classes from the wrong side. "
                    + classpathSplitDiagnostic.describeMissingBuilderApi(request)
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
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
}
