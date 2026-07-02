package sh.zolt.build.incremental;

import java.nio.file.Path;
import java.util.List;

public record IncrementalCompileValidation(
        String fallbackReason,
        List<Path> additionalSources,
        int abiChangedClasses,
        int packagePrivateAbiChangedClasses) {
    public IncrementalCompileValidation {
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        additionalSources = additionalSources == null
                ? List.of()
                : additionalSources.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList();
        abiChangedClasses = Math.max(0, abiChangedClasses);
        packagePrivateAbiChangedClasses = Math.max(0, packagePrivateAbiChangedClasses);
    }

    public static IncrementalCompileValidation success(List<Path> additionalSources) {
        return success(additionalSources, 0, 0);
    }

    public static IncrementalCompileValidation success(
            List<Path> additionalSources,
            int abiChangedClasses,
            int packagePrivateAbiChangedClasses) {
        return new IncrementalCompileValidation(
                "",
                additionalSources,
                abiChangedClasses,
                packagePrivateAbiChangedClasses);
    }

    public static IncrementalCompileValidation fallback(String reason) {
        return new IncrementalCompileValidation(reason, List.of(), 0, 0);
    }

    public boolean hasFallback() {
        return !fallbackReason.isBlank();
    }
}
