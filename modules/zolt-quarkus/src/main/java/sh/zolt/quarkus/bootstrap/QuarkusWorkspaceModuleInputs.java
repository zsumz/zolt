package sh.zolt.quarkus.bootstrap;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;

public record QuarkusWorkspaceModuleInputs(
        Path projectDirectory,
        Path buildDirectory,
        Path mainSourceDirectory,
        Path mainResourceDirectory,
        Path mainOutputDirectory,
        Path testSourceDirectory,
        Path testResourceDirectory,
        Path testOutputDirectory) {
    public QuarkusWorkspaceModuleInputs {
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a project directory.");
        }
        if (buildDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a build directory.");
        }
        if (mainSourceDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a main source directory.");
        }
        if (mainResourceDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a main resource directory.");
        }
        if (mainOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a main output directory.");
        }
        if (testSourceDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a test source directory.");
        }
        if (testResourceDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a test resource directory.");
        }
        if (testOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus workspace module requires a test output directory.");
        }
    }
}
