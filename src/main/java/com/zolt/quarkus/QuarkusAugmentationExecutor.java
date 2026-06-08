package com.zolt.quarkus;

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
        augmentor.augment(request);
        metadataWriter.write(request.projectDirectory(), request.inputFingerprint());
        return new QuarkusAugmentationResult(
                request.outputLayout().augmentationDirectory(),
                request.metadataPath(),
                descriptor,
                request.inputFingerprint());
    }

    private static void prepareOutput(QuarkusAugmentationRequest request) {
        try {
            Files.createDirectories(request.outputLayout().augmentationDirectory());
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not create Quarkus augmentation output directory at "
                            + request.outputLayout().augmentationDirectory()
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }
}
