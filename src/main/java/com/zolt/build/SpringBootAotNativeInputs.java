package com.zolt.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class SpringBootAotNativeInputs {
    private final Path projectRoot;
    private final String outputRoot;

    SpringBootAotNativeInputs(Path projectRoot) {
        this(projectRoot, "target");
    }

    SpringBootAotNativeInputs(Path projectRoot, String outputRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.outputRoot = outputRoot == null || outputRoot.isBlank() ? "target" : outputRoot;
    }

    List<Path> classpathEntries() {
        Path classes = classesDirectory();
        Path resources = resourcesDirectory();
        requireDirectory(classes, "compiled Spring Boot AOT classes");
        requireDirectory(resources, "Spring Boot AOT resources");
        requireNativeMetadata(resources);
        return List.of(classes, resources);
    }

    private Path classesDirectory() {
        return projectRoot.resolve(outputRoot).resolve("spring-aot/main/classes").normalize();
    }

    private Path resourcesDirectory() {
        return projectRoot.resolve(outputRoot).resolve("spring-aot/main/resources").normalize();
    }

    private static void requireDirectory(Path path, String label) {
        if (!Files.isDirectory(path)) {
            throw new NativeImageException(
                    "Spring Boot native AOT output is missing "
                            + label
                            + " at "
                            + path
                            + ". [framework.springBoot.native] enabled = true requires Spring AOT outputs under the configured build output root before invoking Native Image.");
        }
    }

    private static void requireNativeMetadata(Path resources) {
        Path metadataRoot = resources.resolve("META-INF/native-image");
        if (!Files.isDirectory(metadataRoot) || !containsRegularFile(metadataRoot)) {
            throw new NativeImageException(
                    "Spring Boot native AOT metadata is missing at "
                            + metadataRoot
                            + ". [framework.springBoot.native] enabled = true requires Zolt-owned reachability metadata before invoking Native Image.");
        }
    }

    private static boolean containsRegularFile(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.anyMatch(Files::isRegularFile);
        } catch (IOException exception) {
            throw new NativeImageException(
                    "Could not inspect Spring Boot native AOT metadata under "
                            + directory
                            + ". Check filesystem permissions and retry.",
                    exception);
        }
    }
}
