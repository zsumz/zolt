package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class QuarkusAnnotationClasspathSplitDiagnostic {
    private final BootstrapDescriptorReader bootstrapDescriptorReader;

    public QuarkusAnnotationClasspathSplitDiagnostic() {
        this(new QuarkusBootstrapDescriptorReader()::read);
    }

    QuarkusAnnotationClasspathSplitDiagnostic(BootstrapDescriptorReader bootstrapDescriptorReader) {
        if (bootstrapDescriptorReader == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation classpath split diagnostic bootstrap descriptor reader is required.");
        }
        this.bootstrapDescriptorReader = bootstrapDescriptorReader;
    }

    public String describe(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation classpath split diagnostic requires a request.");
        }
        try {
            QuarkusBootstrapDescriptor bootstrapDescriptor =
                    bootstrapDescriptorReader.read(request.descriptor().bootstrapDescriptorFile());
            List<Path> sharedClasspath = sharedClasspath(
                    request.descriptor().testRuntimeClasspath(),
                    bootstrapDescriptor.deploymentClasspath());
            return description(sharedClasspath);
        } catch (QuarkusAugmentationException exception) {
            return "Could not inspect Quarkus deployment classpath ownership from "
                    + request.descriptor().bootstrapDescriptorFile()
                    + ". Run `zolt build`, then run `zolt test` again.";
        }
    }

    private static String description(List<Path> sharedClasspath) {
        return "Classpath ownership: "
                + quarkusBuilderOwnership(sharedClasspath)
                + " Shared test/deployment classpath entries: "
                + sharedClasspath.size()
                + ".";
    }

    private static String quarkusBuilderOwnership(List<Path> sharedClasspath) {
        return sharedClasspath.stream()
                .filter(QuarkusAnnotationClasspathSplitDiagnostic::isQuarkusBuilderJar)
                .findFirst()
                .map(path -> fileName(path)
                        + " is present on both the JUnit test runtime classpath and Quarkus deployment classpath.")
                .orElse("quarkus-builder was not found in both classpaths.");
    }

    private static List<Path> sharedClasspath(List<Path> testRuntimeClasspath, List<Path> deploymentClasspath) {
        Set<Path> deploymentEntries = new LinkedHashSet<>();
        for (Path entry : deploymentClasspath) {
            deploymentEntries.add(normalize(entry));
        }
        Set<Path> shared = new LinkedHashSet<>();
        for (Path entry : testRuntimeClasspath) {
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
