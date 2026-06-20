package com.zolt.quarkus;

public record QuarkusBootstrapHandle(
        Object bootstrap,
        String bootstrapClass,
        String applicationModelClass) {
    public QuarkusBootstrapHandle {
        if (bootstrap == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap handle requires a bootstrap instance.");
        }
        if (bootstrapClass == null || bootstrapClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap handle requires a bootstrap class.");
        }
        if (applicationModelClass == null || applicationModelClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap handle requires an application model class.");
        }
    }
}
