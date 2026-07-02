package sh.zolt.quarkus.production;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.QuarkusBootstrapWorkerResult;
import java.nio.file.Path;

public record QuarkusAugmentationResult(
        Path augmentationDirectory,
        Path metadataPath,
        QuarkusBootstrapDescriptor bootstrapDescriptor,
        String inputFingerprint,
        QuarkusBootstrapWorkerResult workerResult) {
    public QuarkusAugmentationResult {
        if (augmentationDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires an augmentation directory.");
        }
        if (metadataPath == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires a metadata path.");
        }
        if (bootstrapDescriptor == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires a bootstrap descriptor.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires an input fingerprint.");
        }
        if (workerResult == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires a worker result.");
        }
    }
}
