package com.zolt.quarkus;

public final class QuarkusAnnotationWorkerRunner {
    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        return new Result(
                2,
                "error: Quarkus annotation test execution is not implemented yet. "
                        + "Zolt has selected the dedicated Quarkus annotation runner path, but still needs to own "
                        + "Quarkus JUnit discovery, launcher/session listeners, test resources, and test profiles.\n");
    }

    public record Result(int exitCode, String output) {
    }
}
