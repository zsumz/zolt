package com.zolt.quarkus.testworker;

public enum QuarkusTestWorkerPlanStatus {
    PLAIN_JUNIT_READY("plain JUnit ready"),
    QUARKUS_TEST_RUNNER_SELECTED("Quarkus annotation runner selected"),
    BLOCKED_UNSUPPORTED_QUARKUS_TESTS("blocked by unsupported Quarkus test annotations"),
    QUARKUS_TEST_ANNOTATIONS_DISABLED("blocked by descriptor-disabled Quarkus test annotations"),
    MISSING_JUNIT_CONSOLE("blocked by missing JUnit Platform Console"),
    UNSUPPORTED_RUNNER_MODE("blocked by unsupported Quarkus test runner mode");

    private final String displayName;

    QuarkusTestWorkerPlanStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
