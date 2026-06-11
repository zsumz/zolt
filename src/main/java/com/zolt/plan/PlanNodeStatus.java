package com.zolt.plan;

public enum PlanNodeStatus {
    READY("ready"),
    BLOCKED("blocked"),
    SKIPPED("skipped"),
    PLANNED("planned");

    private final String configValue;

    PlanNodeStatus(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }
}
