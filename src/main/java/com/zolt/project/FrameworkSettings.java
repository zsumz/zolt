package com.zolt.project;

public record FrameworkSettings(QuarkusSettings quarkus) {
    public FrameworkSettings {
        quarkus = quarkus == null ? QuarkusSettings.defaults() : quarkus;
    }

    public static FrameworkSettings defaults() {
        return new FrameworkSettings(QuarkusSettings.defaults());
    }
}
