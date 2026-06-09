package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class QuarkusAnnotationLauncherClasspathPlanner {
    private final BootstrapDescriptorReader bootstrapDescriptorReader;

    public QuarkusAnnotationLauncherClasspathPlanner() {
        this(new QuarkusBootstrapDescriptorReader()::read);
    }

    QuarkusAnnotationLauncherClasspathPlanner(BootstrapDescriptorReader bootstrapDescriptorReader) {
        if (bootstrapDescriptorReader == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath planner bootstrap descriptor reader is required.");
        }
        this.bootstrapDescriptorReader = bootstrapDescriptorReader;
    }

    public QuarkusAnnotationLauncherClasspathPlan plan(QuarkusTestRunnerDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launcher classpath planner requires a descriptor.");
        }
        List<Path> launcherClasspath = descriptor.testRuntimeClasspath();
        List<Path> sharedClasspath = sharedClasspath(descriptor, launcherClasspath);
        return new QuarkusAnnotationLauncherClasspathPlan(
                launcherClasspath,
                splitSensitiveArtifacts(sharedClasspath),
                builderApiVisible(launcherClasspath),
                sharedClasspath.size());
    }

    private List<Path> sharedClasspath(
            QuarkusTestRunnerDescriptor descriptor,
            List<Path> launcherClasspath) {
        try {
            return sharedClasspath(
                    launcherClasspath,
                    bootstrapDescriptorReader.read(descriptor.bootstrapDescriptorFile()).deploymentClasspath());
        } catch (QuarkusAugmentationException exception) {
            return List.of();
        }
    }

    private static List<Path> splitSensitiveArtifacts(List<Path> sharedClasspath) {
        return sharedClasspath.stream()
                .filter(QuarkusAnnotationLauncherClasspathPlanner::isQuarkusBuilderJar)
                .toList();
    }

    private static boolean builderApiVisible(List<Path> launcherClasspath) {
        return launcherClasspath.stream().anyMatch(QuarkusAnnotationLauncherClasspathPlanner::isQuarkusBuilderJar);
    }

    private static List<Path> sharedClasspath(List<Path> launcherClasspath, List<Path> deploymentClasspath) {
        Set<Path> deploymentEntries = new LinkedHashSet<>();
        for (Path entry : deploymentClasspath) {
            deploymentEntries.add(normalize(entry));
        }
        Set<Path> shared = new LinkedHashSet<>();
        for (Path entry : launcherClasspath) {
            Path normalized = normalize(entry);
            if (deploymentEntries.contains(normalized)) {
                shared.add(normalized);
            }
        }
        return List.copyOf(shared);
    }

    private static boolean isQuarkusBuilderJar(Path path) {
        return fileName(path).startsWith("quarkus-builder-") && fileName(path).endsWith(".jar");
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    interface BootstrapDescriptorReader {
        QuarkusBootstrapDescriptor read(Path descriptorFile);
    }
}
