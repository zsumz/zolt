package com.zolt.selfhost;

import java.util.List;

public record SelfCheckResult(
        List<SelfCheckStep> steps) {
    public SelfCheckResult {
        steps = List.copyOf(steps);
    }

    public boolean ok() {
        return steps.stream().allMatch(SelfCheckStep::ok);
    }

    public record SelfCheckStep(
            String name,
            boolean ok,
            String message) {
    }
}
