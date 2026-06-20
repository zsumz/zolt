package com.zolt.quarkus;

public record QuarkusApplicationModelHandle(
        Object applicationModel,
        String applicationModelClass,
        int dependencyCount,
        int runtimeDependencyCount,
        int deploymentDependencyCount) {
    public QuarkusApplicationModelHandle {
        if (applicationModel == null) {
            throw new QuarkusAugmentationException("Quarkus application model handle requires a model instance.");
        }
        if (applicationModelClass == null || applicationModelClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application model handle requires a model class.");
        }
        if (dependencyCount < 0 || runtimeDependencyCount < 0 || deploymentDependencyCount < 0) {
            throw new QuarkusAugmentationException("Quarkus application model dependency counts cannot be negative.");
        }
    }
}
