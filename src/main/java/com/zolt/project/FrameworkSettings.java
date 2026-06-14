package com.zolt.project;

import java.util.List;

public record FrameworkSettings(QuarkusSettings quarkus) {
    public FrameworkSettings {
        quarkus = quarkus == null ? QuarkusSettings.defaults() : quarkus;
    }

    public List<String> resolutionFingerprintInputs() {
        return List.of(
                "framework.quarkus\tenabled\t" + Boolean.toString(quarkus.enabled()),
                "framework.quarkus\tpackage\t" + quarkus.packageMode().configValue());
    }

    public static FrameworkSettings defaults() {
        return new FrameworkSettings(QuarkusSettings.defaults());
    }
}
