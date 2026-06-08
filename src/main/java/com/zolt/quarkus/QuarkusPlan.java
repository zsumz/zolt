package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusPlan(
        Path projectDirectory,
        Path applicationClasses,
        String inputFingerprint,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<QuarkusPlanExtension> extensions) {
    public QuarkusPlan {
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusPlanException("Quarkus plan requires an input fingerprint.");
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
