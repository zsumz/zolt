package com.zolt.project;

import java.util.List;

public record FrameworkSettings(SpringBootSettings springBoot, QuarkusSettings quarkus) {
    public FrameworkSettings {
        springBoot = springBoot == null ? SpringBootSettings.defaults() : springBoot;
        quarkus = quarkus == null ? QuarkusSettings.defaults() : quarkus;
    }

    public FrameworkSettings(QuarkusSettings quarkus) {
        this(SpringBootSettings.defaults(), quarkus);
    }

    public List<String> resolutionFingerprintInputs() {
        return List.of(
                "framework.springBoot.native\tenabled\t" + Boolean.toString(springBoot.nativeEnabled()),
                "framework.quarkus\tenabled\t" + Boolean.toString(quarkus.enabled()),
                "framework.quarkus\tpackage\t" + quarkus.packageMode().configValue());
    }

    public static FrameworkSettings defaults() {
        return new FrameworkSettings(SpringBootSettings.defaults(), QuarkusSettings.defaults());
    }
}
