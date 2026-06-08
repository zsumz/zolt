package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusBootstrapDescriptor(
        Path descriptorFile,
        Path runtimeClasspathFile,
        Path deploymentClasspathFile,
        Path projectDirectory,
        Path applicationClasses,
        Path augmentationDirectory,
        Path packageDirectory,
        String packageMode,
        String inputFingerprint,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath) {
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
        if (runtimeClasspath == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap runtime classpath is required.");
        }
        if (deploymentClasspath == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap deployment classpath is required.");
        }
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
    }
}
