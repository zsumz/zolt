package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;

public record QuarkusApplicationModelApi(
        String applicationModelBuilderClass,
        String resolvedDependencyBuilderClass,
        String platformImportsClass,
        String platformImportsImplClass,
        String artifactKeyClass) {
    public static final QuarkusApplicationModelApi DEFAULT = new QuarkusApplicationModelApi(
            "io.quarkus.bootstrap.model.ApplicationModelBuilder",
            "io.quarkus.maven.dependency.ResolvedDependencyBuilder",
            "io.quarkus.bootstrap.model.PlatformImports",
            "io.quarkus.bootstrap.model.PlatformImportsImpl",
            "io.quarkus.maven.dependency.ArtifactKey");

    public QuarkusApplicationModelApi(
            String applicationModelBuilderClass,
            String resolvedDependencyBuilderClass,
            String platformImportsClass,
            String platformImportsImplClass) {
        this(
                applicationModelBuilderClass,
                resolvedDependencyBuilderClass,
                platformImportsClass,
                platformImportsImplClass,
                "io.quarkus.maven.dependency.ArtifactKey");
    }

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
        if (artifactKeyClass == null || artifactKeyClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application model API requires an artifact key class.");
        }
    }
}
