package com.zolt.quality;

public enum QualityCheckSeverity {
    INFO("info"),
    WARN("warn"),
    ERROR("error");

    private final String jsonValue;

    QualityCheckSeverity(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }
}
