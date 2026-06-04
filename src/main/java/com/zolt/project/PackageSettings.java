package com.zolt.project;

public record PackageSettings(PackageMode mode) {
    public PackageSettings {
        mode = mode == null ? PackageMode.THIN : mode;
    }

    public static PackageSettings defaults() {
        return new PackageSettings(PackageMode.THIN);
    }
}
