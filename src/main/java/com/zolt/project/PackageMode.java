package com.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum PackageMode {
    THIN("thin"),
    SPRING_BOOT("spring-boot"),
    UBER("uber");

    private final String configValue;

    PackageMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<PackageMode> fromConfigValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (PackageMode mode : values()) {
            if (mode.configValue.equals(value)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }
}
