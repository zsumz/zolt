package com.zolt.quarkus.bootstrap.descriptor;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.production.QuarkusAugmentationRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class QuarkusBootstrapDescriptorWriter {
    public static final String BOOTSTRAP_CLASS = "io.quarkus.bootstrap.app.QuarkusBootstrap";
    public static final String AUGMENT_ACTION_CLASS = "io.quarkus.bootstrap.app.AugmentAction";

    public QuarkusBootstrapDescriptor write(QuarkusAugmentationRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation request is required.");
        }

        Path augmentationDirectory = request.outputLayout().augmentationDirectory();
        Path descriptorFile = augmentationDirectory.resolve("zolt-bootstrap.properties");
        Path runtimeClasspathFile = augmentationDirectory.resolve("runtime-classpath.txt");
        Path deploymentClasspathFile = augmentationDirectory.resolve("deployment-classpath.txt");
        Path platformPropertiesFile = augmentationDirectory.resolve("platform-properties.txt");
        Path applicationModelFile = augmentationDirectory.resolve("application-model.properties");
        try {
            Files.createDirectories(augmentationDirectory);
            writeClasspath(runtimeClasspathFile, request.runtimeClasspath());
            writeClasspath(deploymentClasspathFile, request.deploymentClasspath());
            writePlatformProperties(platformPropertiesFile, request.platformPropertiesArtifacts());
            Files.writeString(
                    applicationModelFile,
                    applicationModel(request.applicationArtifact(), request.bootstrapDependencies()),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    descriptorFile,
                    descriptor(request, runtimeClasspathFile, deploymentClasspathFile, platformPropertiesFile, applicationModelFile),
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
                platformPropertiesFile,
                applicationModelFile,
                BOOTSTRAP_CLASS,
                AUGMENT_ACTION_CLASS,
                request.projectDirectory(),
                request.applicationClasses(),
                augmentationDirectory,
                request.outputLayout().packageDirectory(),
                request.packageMode().configValue(),
                request.inputFingerprint(),
                request.applicationArtifact(),
                request.runtimeClasspath(),
                request.deploymentClasspath(),
                request.platformPropertiesArtifacts().stream()
                        .map(QuarkusPlatformPropertiesArtifact::path)
                        .sorted()
                        .toList(),
                sortedDependencies(request.bootstrapDependencies()));
    }

    private static String descriptor(
            QuarkusAugmentationRequest request,
            Path runtimeClasspathFile,
            Path deploymentClasspathFile,
            Path platformPropertiesFile,
            Path applicationModelFile) {
        return """
                version=1
                bootstrapClass=%s
                augmentActionClass=%s
                mode=prod
                package=%s
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                platformPropertiesFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                BOOTSTRAP_CLASS,
                AUGMENT_ACTION_CLASS,
                request.packageMode().configValue(),
                request.projectDirectory(),
                request.applicationClasses(),
                request.outputLayout().augmentationDirectory(),
                request.outputLayout().packageDirectory(),
                runtimeClasspathFile,
                deploymentClasspathFile,
                platformPropertiesFile,
                applicationModelFile,
                request.inputFingerprint());
    }

    private static void writeClasspath(Path path, List<Path> entries) throws IOException {
        StringBuilder output = new StringBuilder();
        for (Path entry : entries) {
            output.append(entry).append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }

    private static void writePlatformProperties(
            Path path,
            List<QuarkusPlatformPropertiesArtifact> artifacts) throws IOException {
        StringBuilder output = new StringBuilder();
        for (QuarkusPlatformPropertiesArtifact artifact : artifacts.stream()
                .sorted(Comparator.comparing(candidate -> candidate.packageId() + ":" + candidate.version() + ":" + candidate.path()))
                .toList()) {
            output.append(artifact.path()).append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }

    private static String applicationModel(
            QuarkusApplicationArtifact applicationArtifact,
            List<QuarkusBootstrapDependency> dependencies) {
        List<QuarkusBootstrapDependency> sortedDependencies = sortedDependencies(dependencies);
        StringBuilder output = new StringBuilder();
        output.append("version=1\n");
        output.append("application.groupId=").append(applicationArtifact.packageId().groupId()).append('\n');
        output.append("application.artifactId=").append(applicationArtifact.packageId().artifactId()).append('\n');
        output.append("application.version=").append(applicationArtifact.version()).append('\n');
        output.append("application.classifier=").append(applicationArtifact.classifier()).append('\n');
        output.append("application.type=").append(applicationArtifact.type()).append('\n');
        output.append("application.path=").append(applicationArtifact.path()).append('\n');
        output.append("dependencyCount=").append(sortedDependencies.size()).append('\n');
        for (int index = 0; index < sortedDependencies.size(); index++) {
            QuarkusBootstrapDependency dependency = sortedDependencies.get(index);
            String prefix = "dependency." + index + ".";
            output.append(prefix).append("groupId=").append(dependency.packageId().groupId()).append('\n');
            output.append(prefix).append("artifactId=").append(dependency.packageId().artifactId()).append('\n');
            output.append(prefix).append("version=").append(dependency.version()).append('\n');
            output.append(prefix).append("classifier=").append(dependency.classifier()).append('\n');
            output.append(prefix).append("type=").append(dependency.type()).append('\n');
            output.append(prefix).append("scope=").append(dependency.scope().lockfileName()).append('\n');
            output.append(prefix).append("path=").append(dependency.path()).append('\n');
            output.append(prefix).append("direct=").append(dependency.direct()).append('\n');
        }
        return output.toString();
    }

    private static List<QuarkusBootstrapDependency> sortedDependencies(List<QuarkusBootstrapDependency> dependencies) {
        return dependencies.stream()
                .sorted(Comparator.comparing(QuarkusBootstrapDescriptorWriter::dependencyKey))
                .toList();
    }

    private static String dependencyKey(QuarkusBootstrapDependency dependency) {
        return dependency.scope().lockfileName()
                + ":"
                + dependency.packageId()
                + ":"
                + dependency.version()
                + ":"
                + dependency.path();
    }
}
