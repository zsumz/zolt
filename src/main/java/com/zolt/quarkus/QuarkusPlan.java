package com.zolt.quarkus;

import com.zolt.project.QuarkusPackageMode;
import java.nio.file.Path;
import java.util.List;

public record QuarkusPlan(
        Path projectDirectory,
        Path applicationClasses,
        QuarkusPackageMode packageMode,
        String inputFingerprint,
        QuarkusAugmentationState augmentationState,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<QuarkusPlanExtension> extensions) {
    public QuarkusPlan {
        if (packageMode == null) {
            throw new QuarkusPlanException("Quarkus plan requires a package mode.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusPlanException("Quarkus plan requires an input fingerprint.");
        }
        if (augmentationState == null) {
            throw new QuarkusPlanException("Quarkus plan requires an augmentation metadata state.");
        }
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
    }

    public boolean hasDeploymentInputs() {
        return !deploymentClasspath.isEmpty();
    }

    public boolean allExtensionDeploymentsResolved() {
        return extensions.stream().allMatch(extension -> extension.deploymentArtifactPath().isPresent());
    }
}
