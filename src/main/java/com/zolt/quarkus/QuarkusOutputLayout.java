package com.zolt.quarkus;

import java.nio.file.Path;

public record QuarkusOutputLayout(
        Path augmentationDirectory,
        Path packageDirectory) {
    public QuarkusOutputLayout {
        if (augmentationDirectory == null) {
            throw new QuarkusPlanException("Quarkus output layout requires an augmentation directory.");
        }
        if (packageDirectory == null) {
            throw new QuarkusPlanException("Quarkus output layout requires a package directory.");
        }
    }

    public static QuarkusOutputLayout forProject(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        return new QuarkusOutputLayout(
                root.resolve("target/quarkus").normalize(),
                root.resolve("target/quarkus-app").normalize());
    }
}
