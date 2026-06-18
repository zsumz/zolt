package com.zolt.build;

import com.zolt.project.PackageMode;
import java.util.List;
import java.util.stream.Collectors;

final class PackageModeSupport {
    private PackageModeSupport() {
    }

    static void ensureSupported(PackageMode mode) {
        // All recognized package modes are implemented.
    }

    static PackageException unsupported(PackageMode mode) {
        return new PackageException(
                "Package mode `"
                        + mode.configValue()
                        + "` is not implemented yet. Supported package modes are: "
                        + supportedPackageModes()
                        + ". Intentionally unsupported package modes are: "
                        + unsupportedPackageModes()
                        + ". Use one of the supported modes until uber jar support lands"
                        + ".");
    }

    private static String supportedPackageModes() {
        return List.of(
                        PackageMode.THIN,
                        PackageMode.SPRING_BOOT,
                        PackageMode.WAR,
                        PackageMode.SPRING_BOOT_WAR,
                        PackageMode.QUARKUS)
                .stream()
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }

    private static String unsupportedPackageModes() {
        return List.of(PackageMode.UBER).stream()
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }
}
