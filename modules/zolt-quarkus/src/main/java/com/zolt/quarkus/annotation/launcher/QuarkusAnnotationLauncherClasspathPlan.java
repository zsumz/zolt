package com.zolt.quarkus.annotation.launcher;

import com.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;
import java.util.List;

public record QuarkusAnnotationLauncherClasspathPlan(
        List<Path> launcherClasspath,
        List<Path> junitDiscoveryClasspath,
        List<Path> serviceFilteredArtifacts,
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
        if (junitDiscoveryClasspath == null || junitDiscoveryClasspath.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan requires a JUnit discovery classpath.");
        }
        if (serviceFilteredArtifacts == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan requires service-filtered artifacts.");
        }
        if (sharedDeploymentEntries < 0) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath plan shared deployment entry count cannot be negative.");
        }
        launcherClasspath = List.copyOf(launcherClasspath);
        junitDiscoveryClasspath = List.copyOf(junitDiscoveryClasspath);
        serviceFilteredArtifacts = List.copyOf(serviceFilteredArtifacts);
        splitSensitiveArtifacts = List.copyOf(splitSensitiveArtifacts);
    }
}
