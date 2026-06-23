package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

record IncrementalCompileValidation(
        String fallbackReason,
        List<Path> additionalSources,
        int abiChangedClasses,
        int packagePrivateAbiChangedClasses) {
    IncrementalCompileValidation {
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        additionalSources = additionalSources == null
                ? List.of()
                : additionalSources.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList();
        abiChangedClasses = Math.max(0, abiChangedClasses);
        packagePrivateAbiChangedClasses = Math.max(0, packagePrivateAbiChangedClasses);
    }

    static IncrementalCompileValidation success(List<Path> additionalSources) {
        return success(additionalSources, 0, 0);
    }

    static IncrementalCompileValidation success(
            List<Path> additionalSources,
            int abiChangedClasses,
            int packagePrivateAbiChangedClasses) {
        return new IncrementalCompileValidation(
                "",
                additionalSources,
                abiChangedClasses,
                packagePrivateAbiChangedClasses);
    }

    static IncrementalCompileValidation fallback(String reason) {
        return new IncrementalCompileValidation(reason, List.of(), 0, 0);
    }

    boolean hasFallback() {
        return !fallbackReason.isBlank();
    }
}
