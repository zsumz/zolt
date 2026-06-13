package com.zolt.quarkus;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
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
        try {
            return new QuarkusOutputLayout(
                    ProjectPaths.output(root, "Quarkus augmentation output", "target/quarkus"),
                    ProjectPaths.output(root, "Quarkus package output", "target/quarkus-app"));
        } catch (ProjectPathException exception) {
            throw new QuarkusPlanException(exception.getMessage(), exception);
        }
    }
}
