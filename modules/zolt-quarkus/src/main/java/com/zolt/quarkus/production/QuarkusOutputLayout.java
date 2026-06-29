package com.zolt.quarkus.production;

import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.quarkus.QuarkusPlanException;
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
        return forProject(projectRoot, "target");
    }

    public static QuarkusOutputLayout forProject(Path projectRoot, String outputRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        String effectiveOutputRoot = outputRoot == null || outputRoot.isBlank() ? "target" : outputRoot;
        try {
            return new QuarkusOutputLayout(
                    ProjectPaths.output(root, "Quarkus augmentation output", effectiveOutputRoot + "/quarkus"),
                    ProjectPaths.output(root, "Quarkus package output", effectiveOutputRoot + "/quarkus-app"));
        } catch (ProjectPathException exception) {
            throw new QuarkusPlanException(exception.getMessage(), exception);
        }
    }
}
