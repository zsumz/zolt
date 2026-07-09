package sh.zolt.toolchain.jvm;

import java.util.Optional;

public record JavaRuntimeInfo(
        Optional<String> version,
        Optional<String> featureVersion,
        Optional<String> vendor) {
    public JavaRuntimeInfo {
        version = clean(version);
        featureVersion = clean(featureVersion);
        vendor = clean(vendor);
    }

    public static JavaRuntimeInfo empty() {
        return new JavaRuntimeInfo(Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Optional<String> clean(Optional<String> value) {
        if (value == null || value.isEmpty() || value.orElseThrow().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.orElseThrow().strip());
    }
}
