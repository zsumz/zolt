package com.zolt.quarkus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class QuarkusBootstrapDescriptorWriter {
    public QuarkusBootstrapDescriptor write(QuarkusAugmentationRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation request is required.");
        }

        Path augmentationDirectory = request.outputLayout().augmentationDirectory();
        Path descriptorFile = augmentationDirectory.resolve("zolt-bootstrap.properties");
        Path runtimeClasspathFile = augmentationDirectory.resolve("runtime-classpath.txt");
        Path deploymentClasspathFile = augmentationDirectory.resolve("deployment-classpath.txt");
        try {
            Files.createDirectories(augmentationDirectory);
            writeClasspath(runtimeClasspathFile, request.runtimeClasspath());
            writeClasspath(deploymentClasspathFile, request.deploymentClasspath());
            Files.writeString(
                    descriptorFile,
                    descriptor(request, runtimeClasspathFile, deploymentClasspathFile),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not write Quarkus bootstrap descriptor under "
                            + augmentationDirectory
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
        return new QuarkusBootstrapDescriptor(
                descriptorFile,
                runtimeClasspathFile,
                deploymentClasspathFile,
                request.projectDirectory(),
                request.applicationClasses(),
                augmentationDirectory,
                request.outputLayout().packageDirectory(),
                request.packageMode().configValue(),
                request.inputFingerprint(),
                request.runtimeClasspath(),
                request.deploymentClasspath());
    }

    private static String descriptor(
            QuarkusAugmentationRequest request,
            Path runtimeClasspathFile,
            Path deploymentClasspathFile) {
        return """
                version=1
                bootstrapClass=io.quarkus.bootstrap.app.QuarkusBootstrap
                augmentActionClass=io.quarkus.bootstrap.app.AugmentAction
                mode=prod
                package=%s
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                inputFingerprint=%s
                """.formatted(
                request.packageMode().configValue(),
                request.projectDirectory(),
                request.applicationClasses(),
                request.outputLayout().augmentationDirectory(),
                request.outputLayout().packageDirectory(),
                runtimeClasspathFile,
                deploymentClasspathFile,
                request.inputFingerprint());
    }

    private static void writeClasspath(Path path, List<Path> entries) throws IOException {
        StringBuilder output = new StringBuilder();
        for (Path entry : entries) {
            output.append(entry).append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }
}
