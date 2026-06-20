package com.zolt.project;

public record QuarkusSettings(boolean enabled, QuarkusPackageMode packageMode) {
    public QuarkusSettings {
        packageMode = packageMode == null ? QuarkusPackageMode.FAST_JAR : packageMode;
    }

    public static QuarkusSettings defaults() {
        return new QuarkusSettings(false, QuarkusPackageMode.FAST_JAR);
    }
}
