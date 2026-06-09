package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;
    private final LaunchRequestFactory launchRequestFactory;
    private final LaunchRunner launchRunner;

    public QuarkusAnnotationWorkerRunner() {
        this(
                new QuarkusAnnotationApiProbe()::probe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run);
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        this(
                apiProbe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run);
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
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker API probe is required.");
        }
        if (launchRequestFactory == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch request factory is required.");
        }
        if (launchRunner == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch runner is required.");
        }
        this.apiProbe = apiProbe;
        this.launchRequestFactory = launchRequestFactory;
        this.launchRunner = launchRunner;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        QuarkusAnnotationLaunchRequest launchRequest = launchRequestFactory.create(plan, api);
        QuarkusAnnotationJvmRunner.Result result = launchRunner.run(launchRequest);
        return new Result(result.exitCode(), diagnosedOutput(result));
    }

    private static String diagnosedOutput(QuarkusAnnotationJvmRunner.Result result) {
        if (result.exitCode() == 0 || result.output() == null || result.output().isBlank()) {
            return result.output();
        }
        if (!buildChainBuilderClassloaderSplit(result.output())) {
            return result.output();
        }
        return "error: Quarkus annotation test bootstrap hit a Quarkus classloader split while loading "
                + "io.quarkus.builder.BuildChainBuilder. Zolt reached the dedicated @QuarkusTest runner path, "
                + "but this fixture still needs Quarkus deployment/runtime classloader ownership work before "
                + "Quarkus test annotations can be enabled. Keep using plain JUnit tests for now, or run "
                + "`zolt quarkus test-plan` to inspect blocked tests.\n"
                + result.output().stripTrailing()
                + "\n";
    }

    private static boolean buildChainBuilderClassloaderSplit(String output) {
        return output.contains("io.quarkus.test.junit")
                && output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                && output.contains("cannot be cast to class io.quarkus.builder.BuildChainBuilder");
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
