package com.zolt.project;

public record SpringBootSettings(boolean nativeEnabled) {
    public static SpringBootSettings defaults() {
        return new SpringBootSettings(false);
    }
}
