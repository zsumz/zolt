package com.zolt.build;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ResourceCopier {
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path TEST_RESOURCES = Path.of("src/test/resources");
    private static final Set<String> OUTPUT_DIRECTORY_NAMES = Set.of("target", "build");

    public ResourceCopyResult copyMainResources(Path projectDirectory, BuildSettings settings) {
        return copyResources(
                projectDirectory.resolve(MAIN_RESOURCES).normalize(),
                projectDirectory.resolve(settings.output()).normalize(),
                projectDirectory,
                settings);
    }

    public ResourceCopyResult copyTestResources(Path projectDirectory, BuildSettings settings) {
        return copyResources(
                projectDirectory.resolve(TEST_RESOURCES).normalize(),
                projectDirectory.resolve(settings.testOutput()).normalize(),
                projectDirectory,
                settings);
    }

    private static ResourceCopyResult copyResources(
            Path resourceRoot,
            Path outputDirectory,
            Path projectDirectory,
            BuildSettings settings) {
        if (!Files.isDirectory(resourceRoot)) {
            return new ResourceCopyResult(List.of());
        }

        Path mainOutput = projectDirectory.resolve(settings.output()).normalize();
        Path testOutput = projectDirectory.resolve(settings.testOutput()).normalize();
        try (Stream<Path> paths = Files.walk(resourceRoot)) {
            List<Path> resources = paths
                    .filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .filter(path -> !path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(mainOutput))
                    .filter(path -> !path.startsWith(testOutput))
                    .filter(path -> !startsWithOutputDirectorySegment(resourceRoot.relativize(path)))
                    .sorted()
                    .toList();

            for (Path resource : resources) {
                Path target = outputDirectory.resolve(resourceRoot.relativize(resource)).normalize();
                Files.createDirectories(target.getParent());
                Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new ResourceCopyResult(resources);
        } catch (IOException exception) {
            throw new ResourceCopyException(
                    "Could not copy resources from "
                            + resourceRoot
                            + " to "
                            + outputDirectory
                            + ". Check that the project directories are readable and writable.",
                    exception);
        }
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        return OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
