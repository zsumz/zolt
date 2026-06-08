package com.zolt.quarkus;

public record QuarkusApplicationModelApi(
        String applicationModelBuilderClass,
        String resolvedDependencyBuilderClass) {
    public static final QuarkusApplicationModelApi DEFAULT = new QuarkusApplicationModelApi(
            "io.quarkus.bootstrap.model.ApplicationModelBuilder",
            "io.quarkus.maven.dependency.ResolvedDependencyBuilder");

    public QuarkusApplicationModelApi {
        if (applicationModelBuilderClass == null || applicationModelBuilderClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application model API requires a model builder class.");
        }
        if (resolvedDependencyBuilderClass == null || resolvedDependencyBuilderClass.isBlank()) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model API requires a resolved dependency builder class.");
        }
    }
}
