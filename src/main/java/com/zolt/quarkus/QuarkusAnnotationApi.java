package com.zolt.quarkus;

import java.util.List;

public record QuarkusAnnotationApi(
        String extensionClass,
        String testProfileClass,
        String launcherInterceptorClass,
        List<String> artifactLauncherProviders) {
    public QuarkusAnnotationApi {
        if (extensionClass == null || extensionClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus annotation API requires a test extension class.");
        }
        if (testProfileClass == null || testProfileClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus annotation API requires a test profile class.");
        }
        if (launcherInterceptorClass == null || launcherInterceptorClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus annotation API requires a launcher interceptor class.");
        }
        if (artifactLauncherProviders == null || artifactLauncherProviders.isEmpty()) {
            throw new QuarkusAugmentationException("Quarkus annotation API requires artifact launcher providers.");
        }
        artifactLauncherProviders = List.copyOf(artifactLauncherProviders);
    }
}
