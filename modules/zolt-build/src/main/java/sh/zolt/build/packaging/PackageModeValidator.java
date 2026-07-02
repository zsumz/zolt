package sh.zolt.build.packaging;

import sh.zolt.project.PackageMode;
import java.util.Arrays;
import java.util.stream.Collectors;

final class PackageModeValidator {
    private PackageModeValidator() {
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
