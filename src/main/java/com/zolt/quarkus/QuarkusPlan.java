package com.zolt.quarkus;

import com.zolt.project.QuarkusPackageMode;
import java.nio.file.Path;
import java.util.List;

public record QuarkusPlan(
        Path projectDirectory,
        Path applicationClasses,
        QuarkusPackageMode packageMode,
        QuarkusOutputLayout outputLayout,
        String inputFingerprint,
        QuarkusAugmentationState augmentationState,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<QuarkusBootstrapDependency> bootstrapDependencies,
        List<QuarkusPlanExtension> extensions) {
    public QuarkusPlan {
        if (packageMode == null) {
            throw new QuarkusPlanException("Quarkus plan requires a package mode.");
        }
        if (outputLayout == null) {
            throw new QuarkusPlanException("Quarkus plan requires an output layout.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusPlanException("Quarkus plan requires an input fingerprint.");
        }
        if (augmentationState == null) {
            throw new QuarkusPlanException("Quarkus plan requires an augmentation metadata state.");
        }
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
        bootstrapDependencies = bootstrapDependencies == null ? List.of() : List.copyOf(bootstrapDependencies);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
    }

    public boolean hasDeploymentInputs() {
        return !deploymentClasspath.isEmpty();
    }

    public boolean allExtensionDeploymentsResolved() {
        return extensions.stream().allMatch(extension -> extension.deploymentArtifactPath().isPresent());
    }
}
