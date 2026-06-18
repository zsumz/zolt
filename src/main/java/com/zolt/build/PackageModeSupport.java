package com.zolt.build;

import com.zolt.project.PackageMode;
import java.util.Arrays;
import java.util.stream.Collectors;

final class PackageModeSupport {
    private PackageModeSupport() {
    }

    static void ensureSupported(PackageMode mode) {
        // All recognized package modes are implemented.
    }

    static String supportedPackageModes() {
        return Arrays.stream(PackageMode.values())
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }
}
