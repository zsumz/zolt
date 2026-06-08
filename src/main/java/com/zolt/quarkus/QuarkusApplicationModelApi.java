package com.zolt.quarkus;

public record QuarkusApplicationModelApi(
        String applicationModelBuilderClass,
        String resolvedDependencyBuilderClass,
        String platformImportsClass,
        String platformImportsImplClass) {
    public static final QuarkusApplicationModelApi DEFAULT = new QuarkusApplicationModelApi(
            "io.quarkus.bootstrap.model.ApplicationModelBuilder",
            "io.quarkus.maven.dependency.ResolvedDependencyBuilder",
            "io.quarkus.bootstrap.model.PlatformImports",
            "io.quarkus.bootstrap.model.PlatformImportsImpl");

    public QuarkusApplicationModelApi {
        if (applicationModelBuilderClass == null || applicationModelBuilderClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application model API requires a model builder class.");
        }
        if (resolvedDependencyBuilderClass == null || resolvedDependencyBuilderClass.isBlank()) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model API requires a resolved dependency builder class.");
        }
        if (platformImportsClass == null || platformImportsClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application model API requires a platform imports class.");
        }
        if (platformImportsImplClass == null || platformImportsImplClass.isBlank()) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model API requires a platform imports implementation class.");
        }
    }
}
