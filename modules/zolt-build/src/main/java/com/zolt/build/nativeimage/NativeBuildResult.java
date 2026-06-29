package com.zolt.build.nativeimage;

import com.zolt.build.PackageResult;
import java.nio.file.Path;
import java.util.Optional;

public record NativeBuildResult(
        PackageResult packageResult,
        NativeImageResult nativeImageResult,
        Optional<Path> springBootAotEvidencePath) {
    public NativeBuildResult(PackageResult packageResult, NativeImageResult nativeImageResult) {
        this(packageResult, nativeImageResult, Optional.empty());
    }

    public NativeBuildResult {
        springBootAotEvidencePath = springBootAotEvidencePath == null
                ? Optional.empty()
                : springBootAotEvidencePath;
    }
}
