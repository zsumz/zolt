package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusPlan(
        Path projectDirectory,
        Path applicationClasses,
        List<Path> runtimeClasspath,
        List<Path> deploymentClasspath) {
    public QuarkusPlan {
        runtimeClasspath = List.copyOf(runtimeClasspath);
        deploymentClasspath = List.copyOf(deploymentClasspath);
    }

    public boolean hasDeploymentInputs() {
        return !deploymentClasspath.isEmpty();
    }
}
