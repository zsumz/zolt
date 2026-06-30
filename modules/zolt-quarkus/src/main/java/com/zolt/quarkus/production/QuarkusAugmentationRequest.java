package com.zolt.quarkus.production;

import com.zolt.project.QuarkusPackageMode;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.QuarkusPlanExtension;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusPlatformPropertiesArtifact;
import java.nio.file.Path;
import java.util.List;

public record QuarkusAugmentationRequest(
        Path projectDirectory,
        Path applicationClasses,
        QuarkusPackageMode packageMode,
        QuarkusOutputLayout outputLayout,
        QuarkusApplicationArtifact applicationArtifact,
        String inputFingerprint,
        Path metadataPath,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<QuarkusPlatformPropertiesArtifact> platformPropertiesArtifacts,
        List<QuarkusBootstrapDependency> bootstrapDependencies,
        List<QuarkusPlanExtension> extensions) {
    public QuarkusAugmentationRequest {
        if (projectDirectory == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires a project directory.");
        }
        if (applicationClasses == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires application classes.");
        }
        if (packageMode == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires a package mode.");
        }
        if (outputLayout == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires an output layout.");
        }
        if (applicationArtifact == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires an application artifact.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusPlanException("Quarkus augmentation request requires an input fingerprint.");
        }
        if (metadataPath == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires a metadata path.");
        }
        if (runtimeClasspath == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires a runtime classpath.");
        }
        if (deploymentClasspath == null) {
            throw new QuarkusPlanException("Quarkus augmentation request requires a deployment classpath.");
        }
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
        platformPropertiesArtifacts = platformPropertiesArtifacts == null
                ? List.of()
                : List.copyOf(platformPropertiesArtifacts);
        bootstrapDependencies = bootstrapDependencies == null ? List.of() : List.copyOf(bootstrapDependencies);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
    }
}
