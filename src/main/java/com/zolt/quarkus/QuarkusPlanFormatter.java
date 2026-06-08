package com.zolt.quarkus;

import java.nio.file.Path;

public final class QuarkusPlanFormatter {
    public String format(QuarkusPlan plan) {
        StringBuilder output = new StringBuilder();
        output.append("Quarkus augmentation plan\n");
        output.append("Status: ");
        if (plan.hasDeploymentInputs()) {
            output.append("inputs resolved; augmentation runner not implemented yet\n");
        } else {
            output.append("not ready\n");
        }
        output.append("Application classes: ").append(plan.applicationClasses()).append('\n');
        output.append("Package target: ").append(plan.packageMode().configValue()).append('\n');
        output.append("Input fingerprint: ").append(plan.inputFingerprint()).append('\n');
        output.append("Augmentation metadata: ")
                .append(plan.augmentationState().status().label())
                .append(" (")
                .append(plan.augmentationState().metadataPath());
        plan.augmentationState().recordedInputFingerprint()
                .ifPresent(recorded -> output.append("; recorded ").append(recorded));
        output.append(")\n");
        classpath(output, "Runtime classpath entries", plan.runtimeClasspath());
        classpath(output, "Deployment classpath entries", plan.deploymentClasspath());
        output.append("Quarkus extensions: ").append(plan.extensions().size()).append('\n');
        for (QuarkusPlanExtension extension : plan.extensions()) {
            output.append("  ")
                    .append(extension.runtimePackage())
                    .append(" -> ")
                    .append(extension.deploymentArtifact())
                    .append('\n');
            output.append("    runtime jar: ").append(extension.runtimeArtifact()).append('\n');
            output.append("    deployment jar: ")
                    .append(extension.deploymentArtifactPath()
                            .map(Path::toString)
                            .orElse("missing from zolt.lock"))
                    .append('\n');
        }
        output.append("Next: ");
        if (plan.hasDeploymentInputs()) {
            output.append("implement the Zolt-owned Quarkus augmentation runner with these inputs.\n");
        } else {
            output.append("add a Quarkus extension dependency, run `zolt resolve`, then run `zolt quarkus plan` again.\n");
        }
        return output.toString();
    }

    private static void classpath(StringBuilder output, String label, java.util.List<Path> entries) {
        output.append(label).append(": ").append(entries.size()).append('\n');
        for (Path entry : entries) {
            output.append("  ").append(entry).append('\n');
        }
    }
}
