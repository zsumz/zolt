package com.zolt.plan;

public record PlanBlocker(String code, String message, String nextStep) {
    public PlanBlocker {
        code = requireNonBlank(code, "Plan blocker code");
        message = requireNonBlank(message, "Plan blocker message");
        nextStep = requireNonBlank(nextStep, "Plan blocker next step");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return value;
    }
}
