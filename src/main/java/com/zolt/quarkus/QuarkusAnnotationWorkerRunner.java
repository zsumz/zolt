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
        return new Result(result.exitCode(), result.output());
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
