package sh.zolt.quarkus.production;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorWriter;
import sh.zolt.quarkus.bootstrap.QuarkusBootstrapWorkerResult;
import java.io.IOException;
import java.nio.file.Files;

public final class QuarkusAugmentationExecutor {
    private final QuarkusAugmentor augmentor;
    private final QuarkusBootstrapDescriptorWriter descriptorWriter;
    private final QuarkusAugmentationMetadataWriter metadataWriter;

    public QuarkusAugmentationExecutor(QuarkusAugmentor augmentor) {
        this(augmentor, new QuarkusBootstrapDescriptorWriter(), new QuarkusAugmentationMetadataWriter());
    }

    QuarkusAugmentationExecutor(
            QuarkusAugmentor augmentor,
            QuarkusBootstrapDescriptorWriter descriptorWriter,
            QuarkusAugmentationMetadataWriter metadataWriter) {
        if (augmentor == null) {
            throw new QuarkusAugmentationException("Quarkus augmentor is required.");
        }
        if (descriptorWriter == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor writer is required.");
        }
        if (metadataWriter == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation metadata writer is required.");
        }
        this.augmentor = augmentor;
        this.descriptorWriter = descriptorWriter;
        this.metadataWriter = metadataWriter;
    }

    public QuarkusAugmentationResult augment(QuarkusAugmentationRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation request is required.");
        }
        prepareOutput(request);
        QuarkusBootstrapDescriptor descriptor = descriptorWriter.write(request);
        QuarkusBootstrapWorkerResult workerResult = augmentor.augment(request, descriptor);
        validateWorkerResult(request, workerResult);
        metadataWriter.writeMetadata(request.metadataPath(), request.inputFingerprint());
        return new QuarkusAugmentationResult(
                request.outputLayout().augmentationDirectory(),
                request.metadataPath(),
                descriptor,
                request.inputFingerprint(),
                workerResult);
    }

    private static void prepareOutput(QuarkusAugmentationRequest request) {
        try {
            Files.createDirectories(request.outputLayout().augmentationDirectory());
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not create Quarkus augmentation output directory at "
                            + request.outputLayout().augmentationDirectory()
                            + ". Check that the configured output root is writable and try again.",
                    exception);
        }
    }

    private static void validateWorkerResult(
            QuarkusAugmentationRequest request,
            QuarkusBootstrapWorkerResult workerResult) {
        if (workerResult == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus augmentor did not return a verified worker result. "
                            + "Update Zolt or rerun with a clean Quarkus output directory.");
        }
        if (!request.inputFingerprint().equals(workerResult.inputFingerprint())) {
            throw new QuarkusAugmentationException(
                    "Quarkus worker result fingerprint "
                            + workerResult.inputFingerprint()
                            + " did not match expected fingerprint "
                            + request.inputFingerprint()
                            + ". Rerun zolt build after refreshing Quarkus augmentation inputs.");
        }
        if (!request.outputLayout()
                .packageDirectory()
                .toAbsolutePath()
                .normalize()
                .equals(workerResult.packageDirectory().toAbsolutePath().normalize())) {
            throw new QuarkusAugmentationException(
                    "Quarkus worker result package directory "
                            + workerResult.packageDirectory().toAbsolutePath().normalize()
                            + " did not match expected package directory "
                            + request.outputLayout().packageDirectory().toAbsolutePath().normalize()
                            + ". Check the Quarkus package output layout.");
        }
    }
}
