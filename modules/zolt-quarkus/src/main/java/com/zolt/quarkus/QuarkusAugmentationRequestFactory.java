package com.zolt.quarkus;

public final class QuarkusAugmentationRequestFactory {
    public QuarkusAugmentationRequest create(QuarkusPlan plan) {
        if (!plan.hasDeploymentInputs()) {
            throw new QuarkusPlanException(
                    "No Quarkus deployment artifacts were found in zolt.lock. "
                            + "Add a Quarkus extension dependency, run `zolt resolve`, then run `zolt quarkus plan` again.");
        }
        if (!plan.allExtensionDeploymentsResolved()) {
            throw new QuarkusPlanException(
                    "Some Quarkus runtime extensions do not have matching deployment artifacts in zolt.lock. "
                            + "Run `zolt resolve`, then run `zolt quarkus plan` again.");
        }
        return new QuarkusAugmentationRequest(
                plan.projectDirectory(),
                plan.applicationClasses(),
                plan.packageMode(),
                plan.outputLayout(),
                plan.applicationArtifact(),
                plan.inputFingerprint(),
                plan.augmentationState().metadataPath(),
                plan.runtimeClasspath(),
                plan.deploymentClasspath(),
                plan.platformPropertiesArtifacts(),
                plan.bootstrapDependencies(),
                plan.extensions());
    }
}
