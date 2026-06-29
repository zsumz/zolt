package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;
import java.util.List;

public record QuarkusBootstrapDescriptor(
        Path descriptorFile,
        Path runtimeClasspathFile,
        Path deploymentClasspathFile,
        Path platformPropertiesFile,
        Path applicationModelFile,
        String bootstrapClass,
        String augmentActionClass,
        Path projectDirectory,
        Path applicationClasses,
        Path augmentationDirectory,
        Path packageDirectory,
        String packageMode,
        String inputFingerprint,
        QuarkusApplicationArtifact applicationArtifact,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<Path> platformPropertiesFiles,
        List<QuarkusBootstrapDependency> bootstrapDependencies) {
    public QuarkusBootstrapDescriptor {
        if (descriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor file is required.");
        }
        if (runtimeClasspathFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap runtime classpath file is required.");
        }
        if (deploymentClasspathFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap deployment classpath file is required.");
        }
        if (platformPropertiesFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap platform properties file is required.");
        }
        if (applicationModelFile == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap application model file is required.");
        }
        if (bootstrapClass == null || bootstrapClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap class is required.");
        }
        if (augmentActionClass == null || augmentActionClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus augment action class is required.");
        }
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap project directory is required.");
        }
        if (applicationClasses == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap application classes are required.");
        }
        if (augmentationDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap augmentation directory is required.");
        }
        if (packageDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap package directory is required.");
        }
        if (packageMode == null || packageMode.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap package mode is required.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap input fingerprint is required.");
        }
        if (applicationArtifact == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap application artifact is required.");
        }
        if (runtimeClasspath == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap runtime classpath is required.");
        }
        if (deploymentClasspath == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap deployment classpath is required.");
        }
        if (platformPropertiesFiles == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap platform properties files are required.");
        }
        if (bootstrapDependencies == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap dependencies are required.");
        }
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
        platformPropertiesFiles = List.copyOf(platformPropertiesFiles);
        bootstrapDependencies = List.copyOf(bootstrapDependencies);
    }
}
