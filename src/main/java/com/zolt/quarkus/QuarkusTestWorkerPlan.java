package com.zolt.quarkus;

import java.util.List;

public record QuarkusTestWorkerPlan(
        QuarkusTestRunnerDescriptor descriptor,
        QuarkusTestWorkerPlanStatus status,
        List<QuarkusUnsupportedTest> unsupportedTests) {
    public QuarkusTestWorkerPlan {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plan requires a descriptor.");
        }
        if (status == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plan requires a status.");
        }
        unsupportedTests = unsupportedTests == null ? List.of() : List.copyOf(unsupportedTests);
    }

    public boolean plainJunitReady() {
        return status == QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY;
    }
}
