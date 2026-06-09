package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusAnnotationLauncherClasspathPlan(
        List<Path> launcherClasspath,
        List<Path> splitSensitiveArtifacts,
        boolean builderApiVisible,
        int sharedDeploymentEntries) {
    public QuarkusAnnotationLauncherClasspathPlan {
        if (launcherClasspath == null || launcherClasspath.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan requires a launcher classpath.");
        }
        if (splitSensitiveArtifacts == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan requires split-sensitive artifacts.");
        }
        if (sharedDeploymentEntries < 0) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan shared deployment entry count cannot be negative.");
        }
        launcherClasspath = List.copyOf(launcherClasspath);
        splitSensitiveArtifacts = List.copyOf(splitSensitiveArtifacts);
    }
}
