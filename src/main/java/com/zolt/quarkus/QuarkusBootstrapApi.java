package com.zolt.quarkus;

public record QuarkusBootstrapApi(
        String bootstrapClass,
        String augmentActionClass,
        String builderClass,
        String modeClass) {
    public QuarkusBootstrapApi {
        if (bootstrapClass == null || bootstrapClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API requires a bootstrap class.");
        }
        if (augmentActionClass == null || augmentActionClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API requires an augment action class.");
        }
        if (builderClass == null || builderClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API requires a builder class.");
        }
        if (modeClass == null || modeClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API requires a mode class.");
        }
    }
}
