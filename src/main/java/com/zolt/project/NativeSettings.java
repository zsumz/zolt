package com.zolt.project;

import java.util.List;

public record NativeSettings(
        String imageName,
        String output,
        List<String> args) {
    private static final String DEFAULT_OUTPUT = "target/native";

    public NativeSettings {
        imageName = imageName == null ? "" : imageName;
        output = output == null || output.isBlank() ? DEFAULT_OUTPUT : output;
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static NativeSettings defaults() {
        return new NativeSettings("", DEFAULT_OUTPUT, List.of());
    }

    public NativeSettings withDefaultImageName(String defaultImageName) {
        if (imageName == null || imageName.isBlank()) {
            return new NativeSettings(defaultImageName, output, args);
        }
        return this;
    }
}
