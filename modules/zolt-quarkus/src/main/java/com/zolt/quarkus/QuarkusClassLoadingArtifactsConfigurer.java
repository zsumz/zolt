package com.zolt.quarkus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class QuarkusClassLoadingArtifactsConfigurer {
    private final QuarkusApplicationModelApi api;
    private final QuarkusExtensionMetadataReader metadataReader;

    QuarkusClassLoadingArtifactsConfigurer(
            QuarkusApplicationModelApi api,
            QuarkusExtensionMetadataReader metadataReader) {
        this.api = api;
        this.metadataReader = metadataReader;
    }

    void configure(
            Class<?> applicationModelBuilderClass,
            Object modelBuilder,
            QuarkusBootstrapDescriptor descriptor,
            QuarkusApplicationModelOptions options)
            throws ReflectiveOperationException {
        Set<QuarkusArtifactKey> parentFirstArtifacts = new LinkedHashSet<>(options.parentFirstArtifacts());
        Set<QuarkusArtifactKey> runnerParentFirstArtifacts = new LinkedHashSet<>(options.runnerParentFirstArtifacts());
        Map<QuarkusArtifactKey, Set<String>> removedResources = new LinkedHashMap<>();
        addRemovedResources(removedResources, options.removedResources());
        for (Path runtimeArtifact : descriptor.runtimeClasspath()) {
            if (!Files.isRegularFile(runtimeArtifact)) {
                continue;
            }
            Optional<QuarkusExtensionMetadata> metadata = metadataReader.readIfPresent(runtimeArtifact);
            if (metadata.isEmpty()) {
                continue;
            }
            parentFirstArtifacts.addAll(metadata.orElseThrow().parentFirstArtifacts());
            runnerParentFirstArtifacts.addAll(metadata.orElseThrow().runnerParentFirstArtifacts());
            addRemovedResources(removedResources, metadata.orElseThrow().removedResources());
        }
        if (parentFirstArtifacts.isEmpty() && runnerParentFirstArtifacts.isEmpty() && removedResources.isEmpty()) {
            return;
        }
        Class<?> artifactKeyClass = Class.forName(api.artifactKeyClass());
        for (QuarkusArtifactKey artifactKey : parentFirstArtifacts) {
            applicationModelBuilderClass
                    .getMethod("addParentFirstArtifact", artifactKeyClass)
                    .invoke(modelBuilder, QuarkusApplicationModelFactory.artifactKey(artifactKeyClass, artifactKey));
        }
        for (QuarkusArtifactKey artifactKey : runnerParentFirstArtifacts) {
            applicationModelBuilderClass
                    .getMethod("addRunnerParentFirstArtifact", artifactKeyClass)
                    .invoke(modelBuilder, QuarkusApplicationModelFactory.artifactKey(artifactKeyClass, artifactKey));
        }
        for (Map.Entry<QuarkusArtifactKey, Set<String>> entry : removedResources.entrySet()) {
            applicationModelBuilderClass
                    .getMethod("addRemovedResources", artifactKeyClass, java.util.Collection.class)
                    .invoke(
                            modelBuilder,
                            QuarkusApplicationModelFactory.artifactKey(artifactKeyClass, entry.getKey()),
                            List.copyOf(entry.getValue()));
        }
    }

    private static void addRemovedResources(
            Map<QuarkusArtifactKey, Set<String>> target,
            Map<QuarkusArtifactKey, List<String>> source) {
        for (Map.Entry<QuarkusArtifactKey, List<String>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
    }
}
