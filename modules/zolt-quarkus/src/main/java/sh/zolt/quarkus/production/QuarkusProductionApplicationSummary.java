package sh.zolt.quarkus.production;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;

public record QuarkusProductionApplicationSummary(
        String augmentResultClass,
        int artifactResultCount,
        Path jarPath,
        Path libraryDirectory,
        boolean uberJar,
        Path nativeImagePath) {
    public QuarkusProductionApplicationSummary {
        if (augmentResultClass == null || augmentResultClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus production application summary requires a result class.");
        }
        if (artifactResultCount < 0) {
            throw new QuarkusAugmentationException("Quarkus production application artifact result count cannot be negative.");
        }
        if (jarPath == null && libraryDirectory != null) {
            throw new QuarkusAugmentationException("Quarkus production application library directory requires a jar path.");
        }
    }

    public boolean hasJar() {
        return jarPath != null;
    }

    public boolean hasNativeImage() {
        return nativeImagePath != null;
    }
}
