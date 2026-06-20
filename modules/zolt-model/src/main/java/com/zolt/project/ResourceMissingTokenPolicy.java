package com.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ResourceMissingTokenPolicy {
    FAIL("fail"),
    KEEP("keep");

    private final String configValue;

    ResourceMissingTokenPolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<ResourceMissingTokenPolicy> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(ResourceMissingTokenPolicy::configValue)
                .collect(Collectors.joining(", "));
    }
}
