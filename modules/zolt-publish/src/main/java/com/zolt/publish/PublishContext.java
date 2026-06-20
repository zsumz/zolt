package com.zolt.publish;

public enum PublishContext {
    RELEASE("release");

    private final String configValue;

    PublishContext(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }
}
