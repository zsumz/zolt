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
}
