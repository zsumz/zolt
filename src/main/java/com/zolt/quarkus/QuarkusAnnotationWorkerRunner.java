package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;

    public QuarkusAnnotationWorkerRunner() {
        this(new QuarkusAnnotationApiProbe()::probe);
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker API probe is required.");
        }
        this.apiProbe = apiProbe;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        return new Result(
                2,
                "error: Quarkus annotation test execution is not implemented yet. "
                        + "Detected "
                        + api.extensionClass()
                        + " with launcher interceptor "
                        + api.launcherInterceptorClass()
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
}
