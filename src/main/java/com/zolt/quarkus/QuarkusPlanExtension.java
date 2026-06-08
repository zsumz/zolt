package com.zolt.quarkus;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.Optional;

public record QuarkusPlanExtension(
        PackageId runtimePackage,
        Path runtimeArtifact,
        QuarkusDeploymentArtifact deploymentArtifact,
        Optional<Path> deploymentArtifactPath) {
    public QuarkusPlanExtension {
        if (runtimePackage == null) {
            throw new QuarkusPlanException("Quarkus plan extension requires a runtime package.");
        }
        if (runtimeArtifact == null) {
            throw new QuarkusPlanException("Quarkus plan extension requires a runtime artifact path.");
        }
        if (deploymentArtifact == null) {
            throw new QuarkusPlanException("Quarkus plan extension requires a deployment artifact.");
        }
        deploymentArtifactPath = deploymentArtifactPath == null ? Optional.empty() : deploymentArtifactPath;
    }
}
