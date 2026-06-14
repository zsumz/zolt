package com.zolt.quarkus;

import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyScope;
import java.nio.file.Path;

public record QuarkusBootstrapDependency(
        PackageId packageId,
        String version,
        DependencyScope scope,
        Path path,
        boolean direct) {
    public QuarkusBootstrapDependency {
        if (packageId == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap dependency requires a package id.");
        }
        if (version == null || version.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap dependency requires a version.");
        }
        if (scope == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap dependency requires a scope.");
        }
        if (!scope.entersMainRuntimeClasspath() && scope != DependencyScope.QUARKUS_DEPLOYMENT) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap dependency "
                            + packageId
                            + " uses unsupported scope `"
                            + scope.lockfileName()
                            + "`.");
        }
        if (path == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap dependency requires a jar path.");
        }
    }

    public String type() {
        return "jar";
    }

    public String classifier() {
        return "";
    }
}
