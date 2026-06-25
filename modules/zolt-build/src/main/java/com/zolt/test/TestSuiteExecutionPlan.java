package com.zolt.test;

import java.util.List;

public record TestSuiteExecutionPlan(
        TestSelection selection,
        TestWorkerPoolPlan workerPoolPlan) {
    public TestSuiteExecutionPlan {
        selection = selection == null ? TestSelection.empty() : selection;
        workerPoolPlan = workerPoolPlan == null
                ? new TestWorkerPoolPlan(false, 1, List.of())
                : workerPoolPlan;
    }
}
