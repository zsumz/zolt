package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

record SpringBootAotOutputEvidence(
        Path outputRoot,
        Path sourcesDirectory,
        Path classesDirectory,
        Path resourcesDirectory,
        Path nativeMetadataDirectory,
        List<Path> generatedSources,
        List<Path> generatedClasses,
        List<Path> generatedResources,
        List<Path> reflectionMetadata,
        List<Path> reachabilityMetadata,
        String fingerprint) {
    SpringBootAotOutputEvidence {
        generatedSources = generatedSources == null ? List.of() : List.copyOf(generatedSources);
        generatedClasses = generatedClasses == null ? List.of() : List.copyOf(generatedClasses);
        generatedResources = generatedResources == null ? List.of() : List.copyOf(generatedResources);
        reflectionMetadata = reflectionMetadata == null ? List.of() : List.copyOf(reflectionMetadata);
        reachabilityMetadata = reachabilityMetadata == null ? List.of() : List.copyOf(reachabilityMetadata);
    }
}
