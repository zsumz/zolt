package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;
    private final LaunchRequestFactory launchRequestFactory;

    public QuarkusAnnotationWorkerRunner() {
        this(
                new QuarkusAnnotationApiProbe()::probe,
                new QuarkusAnnotationLaunchRequestFactory()::create);
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        this(apiProbe, new QuarkusAnnotationLaunchRequestFactory()::create);
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory) {
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker API probe is required.");
        }
        if (launchRequestFactory == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch request factory is required.");
        }
        this.apiProbe = apiProbe;
        this.launchRequestFactory = launchRequestFactory;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        QuarkusAnnotationLaunchRequest launchRequest = launchRequestFactory.create(plan, api);
        return new Result(
                2,
                "error: Quarkus annotation test execution is not implemented yet. "
                        + "Detected "
                        + api.extensionClass()
                        + " with launcher interceptor "
                        + api.launcherInterceptorClass()
                        + " and planned "
                        + launchRequest.testClasses().size()
                        + " Quarkus test class(es)"
                        + ", but "
                        + "Zolt has selected the dedicated Quarkus annotation runner path, but still needs to own "
                        + "Quarkus JUnit discovery, launcher/session listeners, test resources, and test profiles.\n");
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
}
