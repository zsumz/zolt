package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusPlan(
        Path projectDirectory,
        Path applicationClasses,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath,
        List<QuarkusPlanExtension> extensions) {
    public QuarkusPlan {
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
