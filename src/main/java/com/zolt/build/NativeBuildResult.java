package com.zolt.build;

public record NativeBuildResult(
        PackageResult packageResult,
        NativeImageResult nativeImageResult) {
}
